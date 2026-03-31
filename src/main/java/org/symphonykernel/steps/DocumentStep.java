package org.symphonykernel.steps;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.LLMRequest;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.transformer.TemplateResolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Flux;

/**
 * DocumentStep fetches a document or image from a URL, extracts its content,
 * splits it into chunks, processes each chunk with an LLM using a configured
 * system prompt, and produces a final answer to the user's question.
 *
 * <p>Supports text-based documents (PDF, DOCX, Excel, plain text),
 * scanned/image-only PDFs (rendered page-by-page via the vision model),
 * and direct image files (JPEG, PNG, TIFF, BMP, GIF, WebP).</p>
 *
 * <p>Knowledge configuration (JSON in {@code data} field):</p>
 * <pre>{@code
 * {
 *   "SystemPrompt": "You are analyzing a document. ...",
 *   "ChunkSize": 4000,
 *   "ChunkOverlap": 200,
 *   "ScannedTextThreshold": 50,
 *   "PdfImageDpi": 150
 * }
 * }</pre>
 *
 * <p>The URL is resolved from the execution context variables ({@code url} field)
 * and the user's question comes from {@code ExecutionContext.getUsersQuery()}.</p>
 */
@Service("DocumentStep")
public class DocumentStep extends BaseStep {

    @Autowired
    private IAIClient aiClient;

    @Autowired
    private TemplateResolver templateResolver;

    @Value("${symphony.document.chunk-size:4000}")
    private int defaultChunkSize;

    @Value("${symphony.document.chunk-overlap:200}")
    private int defaultChunkOverlap;

    @Value("${symphony.document.scanned-text-threshold:50}")
    private int defaultScannedTextThreshold;

    @Value("${symphony.document.pdf-image-dpi:150}")
    private int defaultPdfImageDpi;

    @Value("${symphony.document.parallel-threads:4}")
    private int parallelThreads;

    private ExecutorService executor;

    private ExecutorService getExecutor() {
        if (executor == null) {
            executor = Executors.newFixedThreadPool(parallelThreads);
        }
        return executor;
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public ChatResponse getResponse(ExecutionContext ctx) {
        Knowledge kb = ctx.getKnowledge();
        if (kb == null && ctx.getName() != null) {
            kb = knowledgeBase.GetByName(ctx.getName());
            ctx.setKnowledge(kb);
        }

        String url = resolveUrl(ctx);
        JsonNode config = getConfig(kb);
        String systemPrompt = resolveSystemPrompt(config, kb);
        String userQuestion = ctx.getUsersQuery();
        int chunkSize = getIntConfig(config, "ChunkSize", defaultChunkSize);
        int chunkOverlap = getIntConfig(config, "ChunkOverlap", defaultChunkOverlap);

        logger.info("DocumentStep: fetching document from {}", url);
        FetchResult fetchResult = fetchDocument(url, ctx);

        // Direct image file → vision processing
        if (isImageContentType(fetchResult.contentType)) {
            logger.info("DocumentStep: detected image content type, using vision model");
            String base64 = Base64.getEncoder().encodeToString(fetchResult.bytes);
            String visionPrompt = systemPrompt + "\n\n--- User Question ---\n" + userQuestion;
            String answer = aiClient.processImage(visionPrompt, base64);
            ChatResponse response = makeResponseObject(answer);
            saveStepData(ctx, response.getData());
            return response;
        }

        // PDF — single load for text extraction + page count (avoids 3x parsing)
        if (isPdfContent(fetchResult)) {
            int threshold = getIntConfig(config, "ScannedTextThreshold", defaultScannedTextThreshold);
            int dpi = getIntConfig(config, "PdfImageDpi", defaultPdfImageDpi);
            PdfExtraction extraction = extractPdfInOnePass(fetchResult.bytes);
            if (isScannedPdf(extraction.text, extraction.pageCount, threshold)) {
                logger.info("DocumentStep: PDF appears scanned ({} chars, {} pages), using vision model",
                        extraction.text.length(), extraction.pageCount);
                String answer = processScannedPdf(fetchResult.bytes, systemPrompt, userQuestion, dpi);
                ChatResponse response = makeResponseObject(answer);
                saveStepData(ctx, response.getData());
                return response;
            }
            return processTextDocument(extraction.text, chunkSize, chunkOverlap, systemPrompt, userQuestion, ctx);
        }

        // Other document types (DOCX, Excel, plain text)
        String documentText = extractText(fetchResult.bytes, fetchResult.contentType);
        return processTextDocument(documentText, chunkSize, chunkOverlap, systemPrompt, userQuestion, ctx);
    }

    @Override
    public Flux<String> getResponseStream(ExecutionContext ctx) {
        Knowledge kb = ctx.getKnowledge();
        if (kb == null && ctx.getName() != null) {
            kb = knowledgeBase.GetByName(ctx.getName());
            ctx.setKnowledge(kb);
        }

        String url = resolveUrl(ctx);
        JsonNode config = getConfig(kb);
        String systemPrompt = resolveSystemPrompt(config, kb);
        String userQuestion = ctx.getUsersQuery();
        int chunkSize = getIntConfig(config, "ChunkSize", defaultChunkSize);
        int chunkOverlap = getIntConfig(config, "ChunkOverlap", defaultChunkOverlap);

        logger.info("DocumentStep (stream): fetching document from {}", url);
        FetchResult fetchResult = fetchDocument(url, ctx);

        // Direct image file → vision processing (not streamable, emit as single chunk)
        if (isImageContentType(fetchResult.contentType)) {
            logger.info("DocumentStep (stream): detected image, using vision model");
            String base64 = Base64.getEncoder().encodeToString(fetchResult.bytes);
            String visionPrompt = systemPrompt + "\n\n--- User Question ---\n" + userQuestion;
            String answer = aiClient.processImage(visionPrompt, base64);
            saveStepData(ctx, answer);
            return Flux.just(answer);
        }

        // PDF — single load for text + page count
        if (isPdfContent(fetchResult)) {
            int threshold = getIntConfig(config, "ScannedTextThreshold", defaultScannedTextThreshold);
            int dpi = getIntConfig(config, "PdfImageDpi", defaultPdfImageDpi);
            PdfExtraction extraction = extractPdfInOnePass(fetchResult.bytes);
            if (isScannedPdf(extraction.text, extraction.pageCount, threshold)) {
                logger.info("DocumentStep (stream): scanned PDF, using vision model");
                String answer = processScannedPdf(fetchResult.bytes, systemPrompt, userQuestion, dpi);
                saveStepData(ctx, answer);
                return Flux.just(answer);
            }
            return streamTextDocument(extraction.text, chunkSize, chunkOverlap, systemPrompt, userQuestion, ctx);
        }

        // Other document types
        String documentText = extractText(fetchResult.bytes, fetchResult.contentType);
        return streamTextDocument(documentText, chunkSize, chunkOverlap, systemPrompt, userQuestion, ctx);
    }

    private ChatResponse processTextDocument(String documentText, int chunkSize, int chunkOverlap,
                                              String systemPrompt, String userQuestion, ExecutionContext ctx) {
        List<String> chunks = splitIntoChunks(documentText, chunkSize, chunkOverlap);
        logger.info("DocumentStep: split document into {} chunks (chunkSize={}, overlap={})",
                chunks.size(), chunkSize, chunkOverlap);
        String finalAnswer = processChunksAndAnswer(chunks, systemPrompt, userQuestion, ctx.getModelName());
        ChatResponse response = makeResponseObject(finalAnswer);
        saveStepData(ctx, response.getData());
        return response;
    }

    private Flux<String> streamTextDocument(String documentText, int chunkSize, int chunkOverlap,
                                             String systemPrompt, String userQuestion, ExecutionContext ctx) {
        List<String> chunks = splitIntoChunks(documentText, chunkSize, chunkOverlap);
        logger.info("DocumentStep (stream): {} chunks", chunks.size());

        // Process all chunks in parallel
        String chunkSummaries = processChunksInParallel(chunks, systemPrompt, userQuestion, ctx.getModelName());

        String synthesisPrompt = buildSynthesisPrompt(systemPrompt, chunkSummaries, userQuestion);
        StringBuilder responseAccumulator = new StringBuilder();
         Flux<String> generatingMsg = Flux.just("Generating output:");
        return generatingMsg.concatWith(aiClient.streamExecute(new LLMRequest(synthesisPrompt, userQuestion, null, ctx.getModelName())))
                .doOnNext(responseAccumulator::append)
                .doFinally(signal -> saveStepData(ctx, responseAccumulator.toString()));
    }

    // ==================== URL VALIDATION ====================

    /**
     * Validates a URL to prevent SSRF attacks by blocking private/internal
     * IP addresses and restricting to HTTP(S) schemes only.
     */
    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Document URL cannot be null or empty");
        }
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new SecurityException("Only HTTP and HTTPS URLs are allowed, got: " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SecurityException("URL must have a valid host");
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                throw new SecurityException("URLs pointing to internal/private addresses are not allowed");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Unable to resolve host: " + host);
        }
    }

    // ==================== DOCUMENT FETCHING ====================

    private static class FetchResult {
        final byte[] bytes;
        final String contentType;

        FetchResult(byte[] bytes, String contentType) {
            this.bytes = bytes;
            this.contentType = contentType;
        }
    }

    private static class PdfExtraction {
        final String text;
        final int pageCount;

        PdfExtraction(String text, int pageCount) {
            this.text = text;
            this.pageCount = pageCount;
        }
    }

    /**
     * Extracts text and page count from a PDF in a single load — avoids
     * loading the PDF 2-3 times (which was the main PDF bottleneck).
     */
    private PdfExtraction extractPdfInOnePass(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            int pageCount = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            return new PdfExtraction(text, pageCount);
        } catch (IOException e) {
            logger.error("Error extracting PDF in one pass: {}", e.getMessage());
            return new PdfExtraction("", 0);
        }
    }

    private FetchResult fetchDocument(String url, ExecutionContext ctx) {
        validateUrl(url);
        HttpHeaders headers = ctx.getHttpHeaderProvider() != null ? ctx.getHttpHeaderProvider().getHeader() : null;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            if (headers != null) {
                final HttpURLConnection conn = connection;
                headers.forEach((key, values) ->
                        conn.setRequestProperty(key, String.join(",", values)));
            }
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "*/*");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.connect();

            byte[] fileBytes = readAllBytes(connection.getInputStream(), connection.getContentLength());
            String contentType = connection.getContentType();
            return new FetchResult(fileBytes, contentType);
        } catch (Exception e) {
            logger.error("DocumentStep: failed to fetch document from {}: {}", url, e.getMessage());
            throw new RuntimeException("Error fetching document: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Reads all bytes from an InputStream using a pre-sized buffer when
     * Content-Length is known, avoiding repeated array resizing.
     */
    private byte[] readAllBytes(InputStream in, int contentLength) throws IOException {
        if (contentLength > 0) {
            byte[] buf = new byte[contentLength];
            int offset = 0;
            while (offset < contentLength) {
                int read = in.read(buf, offset, contentLength - offset);
                if (read < 0) break;
                offset += read;
            }
            return buf;
        }
        // Unknown length — read in 8KB blocks
        ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) != -1) {
            baos.write(tmp, 0, n);
        }
        return baos.toByteArray();
    }

    private boolean isImageContentType(String contentType) {
        if (contentType == null) return false;
        return contentType.contains("image/jpeg") || contentType.contains("image/png")
                || contentType.contains("image/tiff") || contentType.contains("image/bmp")
                || contentType.contains("image/gif") || contentType.contains("image/webp");
    }

    private boolean isPdfContent(FetchResult result) {
        if (result.contentType != null && result.contentType.contains("pdf")) return true;
        return isPdf(result.bytes);
    }

    private String extractText(byte[] fileBytes, String contentType) {
        if (contentType != null) {
            if (contentType.contains("pdf")) {
                return extractPdfInOnePass(fileBytes).text;
            } else if (contentType.contains("wordprocessingml.document") || contentType.contains("msword")) {
                return extractTextFromDocx(fileBytes);
            } else if (contentType.contains("spreadsheetml.sheet") || contentType.contains("excel")) {
                return extractTextFromExcel(fileBytes);
            } else if (contentType.contains("text/") || contentType.contains("json") || contentType.contains("xml")) {
                return new String(fileBytes);
            }
        }
        // Fallback: detect by magic bytes
        if (isPdf(fileBytes)) return extractPdfInOnePass(fileBytes).text;
        if (isDocx(fileBytes)) return extractTextFromDocx(fileBytes);
        // Default: treat as text
        return new String(fileBytes);
    }

    // ==================== SCANNED PDF / IMAGE PROCESSING ====================

    private boolean isScannedPdf(String extractedText, int pageCount, int thresholdPerPage) {
        if (pageCount <= 0) return true;
        String trimmed = extractedText == null ? "" : extractedText.trim();
        return trimmed.length() < (long) pageCount * thresholdPerPage;
    }

    private List<String> renderPdfPagesToBase64(byte[] pdfBytes, int dpi) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int pageCount = doc.getNumberOfPages();
            PDFRenderer renderer = new PDFRenderer(doc);
            // Render pages in parallel using CompletableFuture
            List<CompletableFuture<String>> futures = new ArrayList<>(pageCount);
            for (int i = 0; i < pageCount; i++) {
                final int pageIndex = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        BufferedImage image;
                        synchronized (renderer) {
                            image = renderer.renderImageWithDPI(pageIndex, dpi);
                        }
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(image, "png", baos);
                        return Base64.getEncoder().encodeToString(baos.toByteArray());
                    } catch (IOException e) {
                        logger.error("Error rendering PDF page {}: {}", pageIndex, e.getMessage());
                        return null;
                    }
                }, getExecutor()));
            }
            List<String> pages = new ArrayList<>(pageCount);
            for (CompletableFuture<String> f : futures) {
                String result = f.join();
                if (result != null) pages.add(result);
            }
            return pages;
        } catch (IOException e) {
            logger.error("Error rendering PDF pages to images: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String processScannedPdf(byte[] pdfBytes, String systemPrompt, String userQuestion, int dpi) {
        List<String> pageImages = renderPdfPagesToBase64(pdfBytes, dpi);
        if (pageImages.isEmpty()) {
            return "Unable to process scanned PDF: no pages could be rendered.";
        }

        if (pageImages.size() == 1) {
            String visionPrompt = systemPrompt + "\n\n--- User Question ---\n" + userQuestion;
            return aiClient.processImage(visionPrompt, pageImages.get(0));
        }

        // Multi-page: process all pages in parallel via vision model
        int totalPages = pageImages.size();
        List<CompletableFuture<String>> futures = new ArrayList<>(totalPages);
        for (int i = 0; i < totalPages; i++) {
            final int pageNum = i + 1;
            final String pageImage = pageImages.get(i);
            futures.add(CompletableFuture.supplyAsync(() -> {
                String pagePrompt = systemPrompt
                        + "\n\nYou are processing page " + pageNum + " of " + totalPages + " from a scanned document."
                        + "\nExtract all text and relevant information from this page image."
                        + "\nIf this page does not contain relevant information, respond with 'No relevant information on this page.'"
                        + "\n\n--- User Question ---\n" + userQuestion;
                return aiClient.processImage(pagePrompt, pageImage);
            }, getExecutor()));
        }

        StringBuilder pageSummaries = new StringBuilder();
        for (int i = 0; i < futures.size(); i++) {
            String pageResult = futures.get(i).join();
            pageSummaries.append("--- Page ").append(i + 1).append(" of ").append(totalPages).append(" ---\n")
                    .append(pageResult).append("\n\n");
        }

        // Synthesize page results
        String synthesisPrompt = buildSynthesisPrompt(systemPrompt, pageSummaries.toString(), userQuestion);
        return aiClient.execute(new LLMRequest(synthesisPrompt, userQuestion, null, null));
    }

    // ==================== TEXT EXTRACTION ====================

    private String extractTextFromDocx(byte[] bytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        } catch (IOException e) {
            logger.error("Error extracting text from DOCX: {}", e.getMessage());
            return "";
        }
    }

    private String extractTextFromExcel(byte[] bytes) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            workbook.forEach(sheet -> {
                sb.append("Sheet: ").append(sheet.getSheetName()).append("\n");
                sheet.forEach(row -> {
                    row.forEach(cell -> sb.append(cell.toString()).append("\t"));
                    sb.append("\n");
                });
            });
            return sb.toString();
        } catch (IOException e) {
            logger.error("Error extracting text from Excel: {}", e.getMessage());
            return "";
        }
    }

    private boolean isPdf(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private boolean isDocx(byte[] bytes) {
        return bytes.length > 2 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    // ==================== CHUNKING ====================

    /**
     * Splits text into overlapping chunks at sentence/paragraph boundaries when possible.
     */
    private List<String> splitIntoChunks(String text, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        int textLen = text.length();
        if (textLen <= chunkSize) {
            return Collections.singletonList(text);
        }

        // Clamp overlap so it cannot equal or exceed chunkSize (would stall)
        int safeOverlap = Math.min(overlap, chunkSize - 1);
        int step = chunkSize - safeOverlap;
        List<String> chunks = new ArrayList<>((textLen / step) + 1);
        char[] chars = text.toCharArray(); // raw array — avoids charAt bounds checks

        int start = 0;
        while (start < textLen) {
            int end = Math.min(start + chunkSize, textLen);
            if (end < textLen) {
                int breakPoint = findBreakPoint(chars, textLen, start, end);
                if (breakPoint > start) {
                    end = breakPoint;
                }
            }
            chunks.add(new String(chars, start, end - start));
            // Guarantee forward progress: advance at least 1 char
            int next = end - safeOverlap;
            if (next <= start) {
                next = start + 1;
            }
            start = next;
        }
        return chunks;
    }

    /**
     * Finds the best break point in a single backward pass, checking
     * paragraph, sentence and word boundaries together.
     */
    private int findBreakPoint(char[] chars, int len, int start, int end) {
        int halfPoint = start + ((end - start) >> 1);
        int bestSentence = -1;
        int bestWord = -1;

        for (int i = end; i > start; i--) {
            char prev = chars[i - 1];

            if (i > halfPoint) {
                // Paragraph break (\n\n) — highest priority, return immediately
                if (prev == '\n' && i < len && chars[i] == '\n') {
                    return i + 1;
                }
                // Sentence break (.!? followed by whitespace) — record first found
                if (bestSentence < 0
                        && (prev == '.' || prev == '!' || prev == '?')
                        && i < len && Character.isWhitespace(chars[i])) {
                    bestSentence = i;
                }
            }

            // Word boundary (space) — record first found
            if (bestWord < 0 && prev == ' ') {
                bestWord = i;
            }

            // Below halfPoint: no more paragraph/sentence possible; stop if word found
            if (i <= halfPoint && bestWord > 0) {
                break;
            }
        }

        if (bestSentence > 0) return bestSentence;
        if (bestWord > 0) return bestWord;
        return end;
    }

    // ==================== LLM PROCESSING ====================

    private String processChunksAndAnswer(List<String> chunks, String systemPrompt,
                                           String userQuestion, String modelName) {
        if (chunks.isEmpty()) {
            return "No document content available to process.";
        }

        if (chunks.size() == 1) {
            // Single chunk — process directly
            String prompt = systemPrompt + "\n\n--- Document Content ---\n" + chunks.get(0)
                    + "\n\n--- User Question ---\n" + userQuestion;
            return aiClient.execute(new LLMRequest(prompt, userQuestion, null, modelName));
        }

        // Multiple chunks: parallel map-reduce
        String chunkSummaries = processChunksInParallel(chunks, systemPrompt, userQuestion, modelName);

        // Synthesis step: combine chunk results into final answer
        String synthesisPrompt = buildSynthesisPrompt(systemPrompt, chunkSummaries, userQuestion);
        return aiClient.execute(new LLMRequest(synthesisPrompt, userQuestion, null, modelName));
    }

    /**
     * Processes all document chunks in parallel using CompletableFuture,
     * collecting results in order.
     */
    private String processChunksInParallel(List<String> chunks, String systemPrompt,
                                            String userQuestion, String modelName) {
        int totalChunks = chunks.size();
        List<CompletableFuture<String>> futures = new ArrayList<>(totalChunks);

        for (int i = 0; i < totalChunks; i++) {
            final int chunkNum = i + 1;
            final String chunk = chunks.get(i);
            futures.add(CompletableFuture.supplyAsync(() -> {
                String chunkPrompt = buildChunkPrompt(systemPrompt, chunk, userQuestion, chunkNum, totalChunks);
                return aiClient.execute(new LLMRequest(chunkPrompt, userQuestion, null, modelName));
            }, getExecutor()));
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < futures.size(); i++) {
            String chunkResult = futures.get(i).join();
            sb.append("--- Chunk ").append(i + 1).append(" of ").append(totalChunks).append(" ---\n")
                    .append(chunkResult).append("\n\n");
        }
        return sb.toString();
    }

    private String buildChunkPrompt(String systemPrompt, String chunkText,
                                     String userQuestion, int chunkNum, int totalChunks) {
        return systemPrompt
                + "\n\nYou are processing chunk " + chunkNum + " of " + totalChunks + " from a document."
                + "\nExtract all information relevant to the user's question from this chunk."
                + "\nIf this chunk does not contain relevant information, respond with 'No relevant information in this chunk.'"
                + "\n\n--- Document Chunk ---\n" + chunkText;
    }

    private String buildSynthesisPrompt(String systemPrompt, String chunkResults, String userQuestion) {
        return systemPrompt
                + "\n\nBelow are extracted results from processing a document in chunks."
                + "\nSynthesize these into a comprehensive, coherent answer to the user's question."
                + "\nDo not mention chunks or processing steps in your answer."
                + "\n\n--- Chunk Results ---\n" + chunkResults;
    }

    // ==================== CONFIG HELPERS ====================

    private String resolveUrl(ExecutionContext ctx) {
        JsonNode variables = ctx.getVariables();
        if (variables != null && variables.has("url")) {
            return variables.get("url").asText();
        }
        Knowledge kb = ctx.getKnowledge();
        if (kb != null && kb.getUrl() != null && !kb.getUrl().isEmpty()) {
            return kb.getUrl();
        }
        throw new IllegalArgumentException("DocumentStep: no URL provided in variables or knowledge configuration");
    }

    private JsonNode getConfig(Knowledge kb) {
        if (kb != null && kb.getData() != null && !kb.getData().isEmpty()) {
            return getParamNode(kb.getData());
        }
        return objectMapper.createObjectNode();
    }

    private String resolveSystemPrompt(JsonNode config, Knowledge kb) {
        if (config.has("SystemPrompt") && !config.get("SystemPrompt").isNull()) {
            return config.get("SystemPrompt").asText();
        }
        if (kb != null && kb.getSystemPrompt() != null && !kb.getSystemPrompt().isEmpty()) {
            return kb.getSystemPrompt();
        }
        return "You are a helpful assistant analyzing a document. Answer the user's question based on the document content provided.";
    }

    private int getIntConfig(JsonNode config, String field, int defaultValue) {
        if (config.has(field) && config.get(field).isNumber()) {
            return config.get(field).asInt();
        }
        return defaultValue;
    }
}
