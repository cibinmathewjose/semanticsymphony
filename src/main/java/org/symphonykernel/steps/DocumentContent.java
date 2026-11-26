package org.symphonykernel.steps;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.MediaType;

/** Extracts supported document formats and delegates image transcription one page at a time. */
final class DocumentContent {
    static final int MAX_TEXT = 128_000;
    private static final int MAX_PAGES = 50;
    private static final long MAX_PIXELS = 12_000_000;
    private static final int MAX_PNG_BYTES = 8 * 1024 * 1024;
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml";
    private static final Set<String> IMAGES = Set.of("jpeg", "jpg", "png", "bmp", "gif", "tif", "tiff", "webp");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "csv", "tsv", "md", "json", "xml", "log");

    private DocumentContent() { }

    static String extract(byte[] bytes, String contentType, String path, int threshold, int dpi,
            BiFunction<Integer, String, String> transcribe) throws Exception {
        if (bytes.length == 0) {
            return "";
        }
        return switch (FileMagic.valueOf(bytes)) {
            case PDF -> pdf(bytes, threshold, dpi, transcribe);
            case OOXML -> office(bytes);
            case OLE2 -> legacyOffice(bytes);
            default -> {
                String images = images(bytes, transcribe);
                yield images != null ? images : text(bytes, contentType, path);
            }
        };
    }

    private static String pdf(byte[] bytes, int threshold, int dpi, BiFunction<Integer, String, String> transcribe)
            throws IOException {
        try (var pdf = Loader.loadPDF(bytes)) {
            if (!pdf.getCurrentAccessPermission().canExtractContent()) {
                throw new IOException("PDF extraction is not permitted");
            }
            int pages = pdf.getNumberOfPages();
            checkPages(pages);
            var stripper = new PDFTextStripper();
            var renderer = new PDFRenderer(pdf);
            renderer.setSubsamplingAllowed(true);
            var text = new StringBuilder();
            for (int index = 0; index < pages; index++) {
                int page = index + 1;
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String extracted = stripper.getText(pdf).strip();
                String evidence = extracted;
                if (extracted.isBlank() || extracted.length() < threshold) {
                    var box = pdf.getPage(index).getCropBox();
                    checkPixels(Math.ceil(box.getWidth() * dpi / 72d), Math.ceil(box.getHeight() * dpi / 72d));
                    String transcription = transcribe.apply(page, encodePng(renderer.renderImageWithDPI(index, dpi)));
                    if (transcription != null && !transcription.isBlank()) {
                        evidence = extracted.isBlank() ? transcription : extracted + "\n" + transcription;
                    }
                }
                appendPage(text, page, evidence);
            }
            return text.toString();
        }
    }

    private static String images(byte[] bytes, BiFunction<Integer, String, String> transcribe) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            var reader = readers.next();
            try {
                if (!IMAGES.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Unsupported image format.");
                }
                reader.setInput(input);
                int pages = reader.getNumImages(true);
                checkPages(pages);
                var text = new StringBuilder();
                for (int index = 0; index < pages; index++) {
                    checkPixels(reader.getWidth(index), reader.getHeight(index));
                    appendPage(text, index + 1, transcribe.apply(index + 1, encodePng(reader.read(index))));
                }
                return text.toString();
            } finally {
                reader.dispose();
            }
        }
    }

    private static String office(byte[] bytes) throws Exception {
        try (var pkg = OPCPackage.open(new ByteArrayInputStream(bytes))) {
            if (!pkg.getPartsByContentType(DOCX).isEmpty()) {
                try (var document = new XWPFDocument(pkg); var extractor = new XWPFWordExtractor(document)) {
                    return extractor.getText();
                }
            }
            if (!pkg.getPartsByContentType(XLSX).isEmpty()) {
                try (var workbook = new XSSFWorkbook(pkg)) {
                    return spreadsheet(workbook);
                }
            }
        }
        throw new IllegalArgumentException("Unsupported Office document format.");
    }

    private static String legacyOffice(byte[] bytes) throws IOException {
        try (var fs = new POIFSFileSystem(new ByteArrayInputStream(bytes))) {
            if (fs.getRoot().hasEntry("WordDocument")) {
                try (var extractor = new WordExtractor(fs.getRoot())) {
                    return extractor.getText();
                }
            }
            if (fs.getRoot().hasEntry("Workbook") || fs.getRoot().hasEntry("Book")) {
                try (var workbook = WorkbookFactory.create(fs.getRoot())) {
                    return spreadsheet(workbook);
                }
            }
        }
        throw new IllegalArgumentException("Unsupported or encrypted Office document format.");
    }

    private static String spreadsheet(org.apache.poi.ss.usermodel.Workbook workbook) {
        var formatter = new DataFormatter(Locale.ROOT);
        formatter.setUseCachedValuesForFormulaCells(true);
        var text = new StringBuilder();
        for (var sheet : workbook) {
            append(text, "Sheet: " + sheet.getSheetName() + "\n");
            for (var row : sheet) {
                int previous = -1;
                for (var cell : row) {
                    append(text, "\t".repeat(cell.getColumnIndex() - Math.max(previous, 0)));
                    append(text, formatter.formatCellValue(cell));
                    previous = cell.getColumnIndex();
                }
                append(text, "\n");
            }
        }
        return text.toString();
    }

    private static String text(byte[] bytes, String contentType, String path) throws IOException {
        MediaType media = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        String extension = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!("text".equalsIgnoreCase(media.getType()) || media.getSubtype().matches("(?:.*\\+)?(?:json|xml)")
                || (MediaType.APPLICATION_OCTET_STREAM.includes(media) && TEXT_EXTENSIONS.contains(extension)))) {
            throw new IllegalArgumentException("Unsupported document format.");
        }
        Charset charset = media.getCharset();
        if (charset == null) {
            boolean utf16 = bytes.length >= 2 && ((bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe)
                    || (bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff));
            charset = utf16 ? StandardCharsets.UTF_16 : StandardCharsets.UTF_8;
        }
        String decoded = charset.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        if (decoded.chars().anyMatch(ch -> Character.isISOControl(ch) && ch != '\t' && ch != '\n' && ch != '\r')) {
            throw new IllegalArgumentException("The document is not valid text.");
        }
        return decoded.startsWith("\uFEFF") ? decoded.substring(1) : decoded;
    }

    private static String encodePng(BufferedImage image) throws IOException {
        try {
            var output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output) || output.size() > MAX_PNG_BYTES) {
                throw new IOException("Could not encode the document page within PNG size limits");
            }
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } finally {
            image.flush();
        }
    }

    private static void checkPages(int pages) {
        if (pages < 1 || pages > MAX_PAGES) {
            throw new IllegalArgumentException("Document must have between 1 and " + MAX_PAGES + " pages.");
        }
    }

    private static void checkPixels(double width, double height) {
        if (!(width > 0 && height > 0 && width * height <= MAX_PIXELS)) {
            throw new IllegalArgumentException("Document page exceeds the image dimension limit.");
        }
    }

    private static void appendPage(StringBuilder text, int page, String evidence) {
        if (evidence != null && !evidence.isBlank()) {
            append(text, "\n--- Page " + page + " ---\n");
            append(text, evidence);
        }
    }

    private static void append(StringBuilder text, String value) {
        if (value.length() > MAX_TEXT - text.length()) {
            throw new IllegalArgumentException("Document exceeds the extracted text limit.");
        }
        text.append(value);
    }
}