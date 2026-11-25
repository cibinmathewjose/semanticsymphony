package org.symphonykernel.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.net.ssl.HttpsURLConnection;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.LLMRequest;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.IUserSessionBase;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;

class DocumentStepTest {
    private static final String GENERIC = "application/octet-stream";

    @Test
    void detectsWordAndBothExcelFormatsWithoutMimeOrExtension() throws Exception {
        try (var doc = new XWPFDocument(); var buffer = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Word fixture");
            doc.write(buffer);
            assertThat(extractText(buffer.toByteArray())).contains("Word fixture");
        }
        try (var xlsx = new XSSFWorkbook(); var xls = new HSSFWorkbook()) {
            for (Workbook workbook : List.of(xlsx, xls)) {
                try (var buffer = new ByteArrayOutputStream()) {
                    var row = workbook.createSheet("Sales").createRow(0);
                    row.createCell(0).setCellValue("Excel fixture");
                    row.createCell(2).setCellValue(42);
                    workbook.write(buffer);
                    assertThat(extractText(buffer.toByteArray())).contains("Sheet: Sales", "Excel fixture\t\t42");
                }
            }
        }
    }

    @Test
    void sendsEveryTiffPageAsPng() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("TIFF").next();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            for (int page = 0; page < 2; page++) {
                var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
                writer.writeToSequence(new IIOImage(image, null, null), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        var pages = new ArrayList<Integer>();
        String evidence = DocumentContent.extract(bytes.toByteArray(), GENERIC, "/download", 50, 150, (page, png) -> {
            assertThat(Base64.getDecoder().decode(png)).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
            pages.add(page);
            return "Transcription " + page;
        });
        assertThat(pages).containsExactly(1, 2);
        assertThat(evidence).contains("Page 1", "Transcription 1", "Page 2", "Transcription 2");
    }

    @Test
    void transcribesOnlySparsePagesInMixedPdf() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var pdf = new PDDocument()) {
            var digital = new PDPage();
            pdf.addPage(digital);
            pdf.addPage(new PDPage());
            try (var stream = new PDPageContentStream(pdf, digital)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 650);
                stream.showText("This page has enough machine-readable words to skip the vision transcription.");
                stream.endText();
            }
            pdf.save(bytes);
        }
        var pages = new ArrayList<Integer>();
        String evidence = DocumentContent.extract(bytes.toByteArray(), GENERIC, "/download", 50, 72, (page, png) -> {
            pages.add(page);
            return "Scanned page evidence";
        });
        assertThat(pages).containsExactly(2);
        assertThat(evidence).contains("machine-readable words", "Scanned page evidence");
    }

    @Test
    void rejectsUnknownBinaryInsteadOfSendingItToTheModel() {
        assertThatThrownBy(() -> extractText(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported");
    }

    @Test
    void resolvesExactHostsAndBoundsDownloadBytes() throws Exception {
        String hosts = "files.example.com, storage.example.com";
        assertThat(DocumentStep.trustedUri("https://FILES.example.com/file?signature=test", hosts).getRawQuery())
                .isEqualTo("signature=test");
        for (String url : List.of("http://files.example.com/file", "https://files.example.com.example.org/file",
                "https://files.example.com@storage.example.com/file", "https://files.example.com:8443/file")) {
            assertThatThrownBy(() -> DocumentStep.trustedUri(url, hosts)).hasMessageContaining("exact host");
        }
        assertThat(DocumentStep.readLimited(new ByteArrayInputStream(new byte[4]), 4)).hasSize(4);
        assertThatThrownBy(() -> DocumentStep.readLimited(new ByteArrayInputStream(new byte[5]), 4))
                .hasMessageContaining("download size limit");
    }

    @Test
    void syncAndStreamUseTheSamePromptPrecedenceAndEvidence() {
        var inputs = new ArrayList<LLMRequest>();
        var ai = mock(IAIClient.class);
        when(ai.execute(any())).thenAnswer(call -> {
            inputs.add(call.getArgument(0));
            return "answer";
        });
        when(ai.streamExecute(any())).thenAnswer(call -> {
            inputs.add(call.getArgument(0));
            return Flux.just("an", "swer");
        });
        var mapper = new ObjectMapper();
        var step = new DocumentStep() {
            @Override
            Download download(URI uri) {
                assertThat(uri.getHost()).isEqualTo("files.example.com");
                return new Download("Café document evidence".getBytes(StandardCharsets.UTF_8), "text/plain", uri.getPath());
            }
        };
        step.objectMapper = mapper;
        step.sessionBase = mock(IUserSessionBase.class);
        ReflectionTestUtils.setField(step, "aiClient", ai);
        ReflectionTestUtils.setField(step, "allowedHosts", "files.example.com");
        var knowledge = new Knowledge();
        knowledge.setData("{\"SystemPrompt\":\"Use short sentences\",\"UserPrompt\":\"configured question\"}");
        var ctx = new ExecutionContext().setUsersQuery("chat question").setKnowledge(knowledge);
        ctx.setVariables(mapper.createObjectNode().put("url", "https://files.example.com/test")
                .put("userPrompt", "runtime question"));

        assertThat(step.getResponse(ctx).getMessage()).isEqualTo("answer");
        var stream = step.getResponseStream(ctx);
        assertThat(inputs).hasSize(1);
        assertThat(stream.collectList().block(Duration.ofSeconds(5))).containsExactly("an", "swer");
        assertThat(inputs).hasSize(2).allSatisfy(request -> {
            assertThat(request.getSystemMessage()).contains("Use short sentences").doesNotContain("Café document evidence");
            assertThat(request.getUserPrompt()).startsWith("runtime question").contains("Café document evidence")
                    .doesNotContain("configured question", "chat question");
        });
    }

    @Test
    void followsRedirectsOnlyWhenEnabledAndUsesFinalPath() throws Exception {
        var requests = new ArrayList<URI>();
        var initial = redirect(302, "//storage.example.com/report.csv");
        var relative = redirect(307, "?signature=test");
        var success = mock(HttpsURLConnection.class);
        when(success.getResponseCode()).thenReturn(200);
        when(success.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] {42}));
        var step = redirectStep(requests, List.of(initial, relative, success));
        URI start = URI.create("https://files.example.com/download");

        assertThatThrownBy(() -> step.download(start)).hasMessageContaining("redirects are disabled");
        assertThat(requests).containsExactly(start);
        requests.clear();
        ReflectionTestUtils.setField(step, "followRedirects", true);
        assertThat(step.download(start).path()).isEqualTo("/report.csv");
        assertThat(requests).containsExactly(start, URI.create("https://storage.example.com/report.csv"),
                URI.create("https://storage.example.com/report.csv?signature=test"));
        verify(initial, times(2)).disconnect();
        verify(relative).disconnect();
        verify(success).setInstanceFollowRedirects(false);
        verify(success).disconnect();
    }

    @Test
    void blocksUntrustedRedirectsAndCapsRedirectChains() throws Exception {
        URI start = URI.create("https://files.example.com/download");
        for (String target : List.of("http://files.example.com/file", "https://unlisted.example.com/file")) {
            var requests = new ArrayList<URI>();
            var step = redirectStep(requests, List.of(redirect(301, target)));
            ReflectionTestUtils.setField(step, "followRedirects", true);
            assertThatThrownBy(() -> step.download(start)).hasMessageContaining("exact host");
            assertThat(requests).containsExactly(start);
        }
        var requests = new ArrayList<URI>();
        var loop = redirect(308, "/download");
        var step = redirectStep(requests, Collections.nCopies(6, loop));
        ReflectionTestUtils.setField(step, "followRedirects", true);
        assertThatThrownBy(() -> step.download(start)).hasMessageContaining("redirect limit");
        assertThat(requests).hasSize(6);
        verify(loop, times(6)).disconnect();
    }

    @Test
    void rejectsMissingOrMalformedRedirectLocations() throws Exception {
        URI start = URI.create("https://files.example.com/download");
        for (String location : List.of("", "/invalid path")) {
            var requests = new ArrayList<URI>();
            var response = redirect(303, location);
            var step = redirectStep(requests, List.of(response));
            ReflectionTestUtils.setField(step, "followRedirects", true);
            assertThatThrownBy(() -> step.download(start)).hasMessageContaining("Location header");
            assertThat(requests).containsExactly(start);
            verify(response).disconnect();
        }
    }

    private static DocumentStep redirectStep(List<URI> requests, List<HttpsURLConnection> responses) {
        var step = new DocumentStep() {
            @Override
            HttpsURLConnection openConnection(URI uri) {
                requests.add(uri);
                return responses.get(requests.size() - 1);
            }
        };
        ReflectionTestUtils.setField(step, "allowedHosts", "files.example.com,storage.example.com");
        return step;
    }

    private static HttpsURLConnection redirect(int status, String location) throws Exception {
        var connection = mock(HttpsURLConnection.class);
        when(connection.getResponseCode()).thenReturn(status);
        when(connection.getHeaderField("Location")).thenReturn(location);
        return connection;
    }

    private static String extractText(byte[] bytes) throws Exception {
        return DocumentContent.extract(bytes, GENERIC, "/download", 50, 150, (page, png) -> {
            throw new AssertionError("Unexpected vision call");
        });
    }
}