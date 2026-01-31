package org.symphonykernel.steps;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.IknowledgeBase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * FileStep is responsible for processing files and extracting text from various formats,
 * such as DOCX, Excel, and PDF. It integrates with the Symphony Kernel for file-based operations.
 */
@Service("FileStep")
public class FileStep extends  BaseStep {

    private static final Logger logger = LoggerFactory.getLogger(FileStep.class);

    @Autowired
    IknowledgeBase knowledgeBase;

    @Autowired
    IAIClient azureOpenAIHelper;


	@Override
    public ChatResponse getResponse(ExecutionContext ctx) {
        ArrayNode node = getData(ctx);
        saveStepData(ctx, node);
        ChatResponse a = new ChatResponse();
        a.setData(node);
        return a;
    }

	private ArrayNode getData(ExecutionContext ctx) {
		HttpHeaders headers = ctx.getHttpHeaderProvider() != null ? ctx.getHttpHeaderProvider().getHeader() : null;
        String url = ctx.getVariables().findValue("url").asText();
        String data = null;
        String type=null;
        long startTime = System.currentTimeMillis(); // Start time logging
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            if (headers != null) {
                headers.entrySet().forEach(entry -> {
                    connection.setRequestProperty(entry.getKey(), String.join(",", entry.getValue()));
                });
            }

            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "*/*");
            connection.connect();

            int statusCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();

            String contentType = connection.getContentType();
            byte[] fileBytes = IOUtils.toByteArray(connection.getInputStream());
            //data = Base64.getEncoder().encodeToString(fileBytes);
            
            if (contentType != null && contentType.contains("wordprocessingml.document")) {
                type="word";
                //data = extractTextFromDocx(new ByteArrayInputStream(fileBytes));
            } else if (contentType != null && contentType.contains("pdf")) {
                type="pdf";
                data = extractTextFromPdf(new ByteArrayInputStream(fileBytes));
            }
            else if (contentType != null && contentType.contains("excel")) {
                type="excel";
                //data = extractTextFromExcel(new ByteArrayInputStream(fileBytes));
            } else if (isExcel(fileBytes)) {
                type="excel";
               // data = extractTextFromExcel(new ByteArrayInputStream(fileBytes));
            }
            else if (isPdf(fileBytes)) {
                type="pdf";
                //data = extractTextFromPdf(new ByteArrayInputStream(fileBytes));
            } else if (isDocx(fileBytes)) {
                type="word";
                //data = extractTextFromDocx(new ByteArrayInputStream(fileBytes));
            }
        } catch (Exception e) {
            String str = "Failed to fetch " + url + " Error :" + e.getMessage();
            logger.error(str, e);
            data = str;
        } finally {
            long endTime = System.currentTimeMillis(); // End time logging
            logger.info("Time taken to fetch file from URL (" + url + "): " + (endTime - startTime) + " ms");
        }

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode node = mapper.createArrayNode();
        type="image";
        JsonNode jsonObject = process(ctx.getKnowledge(),type,data, mapper);
        node.add(jsonObject);
		return node;
	}

	private JsonNode process(Knowledge kb,String type, String data, ObjectMapper mapper) {
		String systemPrompt=null;
		if(kb!=null)
			{
			systemPrompt=kb.getData();
			}
		 if (systemPrompt != null && !systemPrompt.isEmpty()) {
			 String result = azureOpenAIHelper.processImage(systemPrompt, data );
			 try {
				return mapper.readTree(result);
			} catch (JsonProcessingException e) {
				e.printStackTrace();		
                data=result;		
			}
		 }		
		ObjectNode jsonObject = mapper.createObjectNode();
        jsonObject.put("data", data);
		return jsonObject;
		 
	}
    private String getImagebase64Text(PDDocument document, int fromPage, int toPage) {
        try {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            StringBuilder base64Images = new StringBuilder();
            for (int page = fromPage; page <= toPage && page < document.getNumberOfPages(); page++) {
                BufferedImage image = pdfRenderer.renderImage(page,3);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                // Save the image to a file
                String fileName = "page_" + page + ".png";
                ImageIO.write(image, "png", new java.io.File(fileName));
              
                // Read the image from the file path
                BufferedImage imageFromFile = ImageIO.read(new java.io.File(fileName));
               
                BufferedImage grayscaleImage = new BufferedImage(imageFromFile.getWidth(), imageFromFile.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
                grayscaleImage.getGraphics().drawImage(imageFromFile, 0, 0, null);

                // Write the grayscale image to a ByteArrayOutputStream
                ByteArrayOutputStream grayscaleBaos = new ByteArrayOutputStream();
                ImageIO.write(grayscaleImage, "png", grayscaleBaos);

                // Get the Base64 encoded string of the grayscale image
                String grayscaleBase64Image = Base64.getEncoder().encodeToString(grayscaleBaos.toByteArray());
                
                base64Images.append(grayscaleBase64Image).append(",");
            }

            // Remove trailing comma if present
            if (base64Images.length() > 0) {
                base64Images.setLength(base64Images.length() - 1);
            }

            return base64Images.toString();
        } catch (IOException e) {
            logger.error("Error rendering pages as images: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Extracts text content from a DOCX file provided as a ByteArrayInputStream.
     *
     * @param docxInputStream the input stream containing the DOCX file data
     * @return the extracted text from the DOCX file
     */
    public String extractTextFromDocx(ByteArrayInputStream docxInputStream) {
        XWPFDocument document = null;
        XWPFWordExtractor extractor = null;
        try {
            // Load the DOCX document from the InputStream
            document = new XWPFDocument(docxInputStream);

            // Create an XWPFWordExtractor to extract text
            extractor = new XWPFWordExtractor(document);

            // Get the text from the document
            String text = extractor.getText();

            return text;

        } catch (IOException e) {
            System.err.println("Error extracting text from DOCX: " + e.getMessage());
            return ""; // Return empty string on error
        } finally {
            // Close the extractor and document to release resources
            if (extractor != null) {
                try {
                    extractor.close();
                } catch (IOException e) {
                    System.err.println("Error closing DOCX extractor: " + e.getMessage());
                }
            }
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    System.err.println("Error closing DOCX document: " + e.getMessage());
                }
            }
            // ByteArrayInputStream does not need explicit closing here.
        }
    }

    private boolean isPdf(byte[] bytes) {
        return bytes.length > 4 &&
               bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private boolean isExcel(byte[] bytes) {
        return bytes.length > 4 &&
               bytes[0] == 'P' && bytes[1] == 'K' &&
               bytes[2] == 3 && bytes[3] == 4; // Common for .xlsx files
    }

    private boolean isDocx(byte[] bytes) {
      
        return bytes.length > 2 &&
               bytes[0] == 'P' && bytes[1] == 'K';
    }
    
    /**
     * Extracts text content from an Excel file provided as a ByteArrayInputStream.
     *
     * @param excelInputStream the input stream containing the Excel file data
     * @return the extracted text from the Excel file
     */
    public String extractTextFromExcel(ByteArrayInputStream excelInputStream) {
        Workbook workbook = null;
        StringBuilder extractedText = new StringBuilder();
        try {
            // Load the Excel workbook from the InputStream
            workbook = new XSSFWorkbook(excelInputStream);

            // Iterate through all sheets in the workbook
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                extractedText.append("Sheet: ").append(sheet.getSheetName()).append("\n");

                // Iterate through all rows in the sheet
                for (Row row : sheet) {
                    // Iterate through all cells in the row
                    for (Cell cell : row) {
                        extractedText.append(cell.toString()).append("\t");
                    }
                    extractedText.append("\n");
                }
            }

            return extractedText.toString();

        } catch (IOException e) {
            System.err.println("Error extracting text from Excel: " + e.getMessage());
            return ""; // Return empty string on error
        } finally {
            // Close the workbook to release resources
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    System.err.println("Error closing Excel workbook: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Extracts text content from a PDF file provided as a ByteArrayInputStream.
     *
     * @param pdfInputStream the input stream containing the PDF file data
     * @return the extracted text from the PDF file
     */
    public String extractTextFromPdf(ByteArrayInputStream pdfInputStream) {
        PDDocument document = null;
        try {
           
            // Load the PDF document from the InputStream
            byte[] pdfBytes = IOUtils.toByteArray(pdfInputStream);
            document = org.apache.pdfbox.Loader.loadPDF(pdfBytes);
            String text = getImagebase64Text(document,0,3);
            // Create a PDFTextStripper to extract text
            //PDFTextStripper pdfStripper = new PDFTextStripper();

            // Get the text from the document
            //String text = pdfStripper.getText(document);

            return text;

        } catch (IOException e) {
            System.err.println("Error extracting text from PDF: " + e.getMessage());
            return ""; // Return empty string on error
        } finally {
            // Close the document to release resources
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    System.err.println("Error closing PDF document: " + e.getMessage());
                }
            }
            // The ByteArrayInputStream itself doesn't need to be explicitly closed in a finally block
            // because it doesn't hold external resources that need to be released in the same way
            // as a file stream or network stream. Its data is in memory.
        }
    }
}
