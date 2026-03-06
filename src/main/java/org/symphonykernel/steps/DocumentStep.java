package org.symphonykernel.steps;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
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

        // PDF → try text extraction, fall back to vision if scanned
        if (isPdfContent(fetchResult)) {
            int threshold = getIntConfig(config, "ScannedTextThreshold", defaultScannedTextThreshold);
            int dpi = getIntConfig(config, "PdfImageDpi", defaultPdfImageDpi);
            String pdfText = extractTextFromPdf(fetchResult.bytes);
            int pageCount = getPdfPageCount(fetchResult.bytes);
            if (isScannedPdf(pdfText, pageCount, threshold)) {
                logger.info("DocumentStep: PDF appears scanned ({} chars, {} pages), using vision model",
                        pdfText.length(), pageCount);
                String answer = processScannedPdf(fetchResult.bytes, systemPrompt, userQuestion, dpi);
                ChatResponse response = makeResponseObject(answer);
                saveStepData(ctx, response.getData());
                return response;
            }
            // Text-based PDF — proceed with chunk processing
            return processTextDocument(pdfText, chunkSize, chunkOverlap, systemPrompt, userQuestion, ctx);
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

        // PDF → try text extraction, fall back to vision if scanned
        if (isPdfContent(fetchResult)) {
            int threshold = getIntConfig(config, "ScannedTextThreshold", defaultScannedTextThreshold);
            int dpi = getIntConfig(config, "PdfImageDpi", defaultPdfImageDpi);
            String pdfText = extractTextFromPdf(fetchResult.bytes);
            int pageCount = getPdfPageCount(fetchResult.bytes);
            if (isScannedPdf(pdfText, pageCount, threshold)) {
                logger.info("DocumentStep (stream): scanned PDF, using vision model");
                String answer = processScannedPdf(fetchResult.bytes, systemPrompt, userQuestion, dpi);
                saveStepData(ctx, answer);
                return Flux.just(answer);
            }
            return streamTextDocument(pdfText, chunkSize, chunkOverlap, systemPrompt, userQuestion, ctx);
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

        StringBuilder chunkSummaries = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkPrompt = buildChunkPrompt(systemPrompt, chunks.get(i), userQuestion, i + 1, chunks.size());
            String chunkResult = aiClient.execute(new LLMRequest(chunkPrompt, userQuestion, null, ctx.getModelName()));
            chunkSummaries.append("--- Chunk ").append(i + 1).append(" ---\n").append(chunkResult).append("\n");
        }

        String synthesisPrompt = buildSynthesisPrompt(systemPrompt, chunkSummaries.toString(), userQuestion);
        StringBuilder responseAccumulator = new StringBuilder();
        return aiClient.streamExecute(new LLMRequest(synthesisPrompt, userQuestion, null, ctx.getModelName()))
                .doOnNext(responseAccumulator::append)
                .doFinally(signal -> saveStepData(ctx, responseAccumulator.toString()));
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

    private FetchResult fetchDocument(String url, ExecutionContext ctx) {
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

            byte[] fileBytes = IOUtils.toByteArray(connection.getInputStream());
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
                return extractTextFromPdf(fileBytes);
            } else if (contentType.contains("wordprocessingml.document") || contentType.contains("msword")) {
                return extractTextFromDocx(fileBytes);
            } else if (contentType.contains("spreadsheetml.sheet") || contentType.contains("excel")) {
                return extractTextFromExcel(fileBytes);
            } else if (contentType.contains("text/") || contentType.contains("json") || contentType.contains("xml")) {
                return new String(fileBytes);
            }
        }
        // Fallback: detect by magic bytes
        if (isPdf(fileBytes)) return extractTextFromPdf(fileBytes);
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

    private int getPdfPageCount(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return doc.getNumberOfPages();
        } catch (IOException e) {
            logger.error("Error getting PDF page count: {}", e.getMessage());
            return 0;
        }
    }

    private List<String> renderPdfPagesToBase64(byte[] pdfBytes, int dpi) {
        List<String> pages = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, dpi);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                pages.add(Base64.getEncoder().encodeToString(baos.toByteArray()));
            }
        } catch (IOException e) {
            logger.error("Error rendering PDF pages to images: {}", e.getMessage());
        }
        return pages;
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

        // Multi-page: extract info from each page, then synthesize
        StringBuilder pageSummaries = new StringBuilder();
        for (int i = 0; i < pageImages.size(); i++) {
            String pagePrompt = systemPrompt
                    + "\n\nYou are processing page " + (i + 1) + " of " + pageImages.size() + " from a scanned document."
                    + "\nExtract all text and relevant information from this page image."
                    + "\nIf this page does not contain relevant information, respond with 'No relevant information on this page.'"
                    + "\n\n--- User Question ---\n" + userQuestion;
            String pageResult = aiClient.processImage(pagePrompt, pageImages.get(i));
            pageSummaries.append("--- Page ").append(i + 1).append(" of ").append(pageImages.size()).append(" ---\n")
                    .append(pageResult).append("\n\n");
        }

        // Synthesize page results
        String synthesisPrompt = buildSynthesisPrompt(systemPrompt, pageSummaries.toString(), userQuestion);
        return aiClient.execute(new LLMRequest(synthesisPrompt, userQuestion, null, null));
    }

    // ==================== TEXT EXTRACTION ====================

    private String extractTextFromPdf(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (IOException e) {
            logger.error("Error extracting text from PDF: {}", e.getMessage());
            return "";
        }
    }

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
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            // Try to break at a paragraph or sentence boundary
            if (end < text.length()) {
                int breakPoint = findBreakPoint(text, start, end);
                if (breakPoint > start) {
                    end = breakPoint;
                }
            }
            chunks.add(text.substring(start, end));
            start = end - overlap;
            if (start < 0) start = 0;
            // Prevent infinite loop if overlap >= chunk text
            if (start >= end) break;
        }
        return chunks;
    }

    private int findBreakPoint(String text, int start, int end) {
        // Prefer paragraph break
        int lastParagraph = text.lastIndexOf("\n\n", end);
        if (lastParagraph > start + (end - start) / 2) {
            return lastParagraph + 2;
        }
        // Then sentence break
        int lastSentence = -1;
        for (int i = end; i > start + (end - start) / 2; i--) {
            char c = text.charAt(i - 1);
            if (c == '.' || c == '!' || c == '?') {
                if (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                    lastSentence = i;
                    break;
                }
            }
        }
        if (lastSentence > start) {
            return lastSentence;
        }
        // Fall back to word boundary
        int lastSpace = text.lastIndexOf(' ', end);
        if (lastSpace > start) {
            return lastSpace + 1;
        }
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

        // Multiple chunks: map-reduce approach
        StringBuilder chunkSummaries = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkPrompt = buildChunkPrompt(systemPrompt, chunks.get(i), userQuestion, i + 1, chunks.size());
            String chunkResult = aiClient.execute(new LLMRequest(chunkPrompt, userQuestion, null, modelName));
            chunkSummaries.append("--- Chunk ").append(i + 1).append(" of ").append(chunks.size()).append(" ---\n")
                    .append(chunkResult).append("\n\n");
        }

        // Synthesis step: combine chunk results into final answer
        String synthesisPrompt = buildSynthesisPrompt(systemPrompt, chunkSummaries.toString(), userQuestion);
        return aiClient.execute(new LLMRequest(synthesisPrompt, userQuestion, null, modelName));
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
