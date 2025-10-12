package org.symphonykernel.steps;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.config.SharePointConfig;
import org.symphonykernel.core.IStep;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemSearchParameterSet;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;
/**
 * The SharePointSearchStep class provides functionality for interacting with SharePoint
 * using Microsoft Graph API. It includes methods for searching SharePoint content,
 * retrieving access tokens, and downloading file content. This class implements the IStep
 * interface to integrate with the Symphony Kernel framework.
 */

@Service
public class SharePointSearchStep extends BaseStep {

    // Define the scope for Microsoft Graph API
    private String scopes = "https://graph.microsoft.com/.default";

    // Endpoint for SharePoint search API
    private final String SEARCH_API_ENDPOINT;

    private static final Logger logger = LoggerFactory.getLogger(SharePointSearchStep.class);

    /**
     * Constructor for SharePointSearchStep.
     * Initializes the SharePoint configuration and ObjectMapper.
     *
     * @param conf      The SharePoint configuration object.
     * @param objMapper The ObjectMapper for JSON processing.
     */
    @Autowired
    public SharePointSearchStep(SharePointConfig conf, ObjectMapper objMapper) {
        // Initialize SharePoint configuration and ObjectMapper
        sharepointConfig = conf;
        mapper = objMapper;
        // Construct the search API endpoint using the domain from configuration
        SEARCH_API_ENDPOINT = "https://" + sharepointConfig.getDomain() + "/_api/search/query";
    }

    ObjectMapper mapper;
    SharePointConfig sharepointConfig;

    /**
     * Retrieves an access token for Microsoft Graph API using client credentials.
     *
     * @param clientId     The client ID of the Azure AD application.
     * @param clientSecret The client secret of the Azure AD application.
     * @param tenantId     The tenant ID of the Azure AD directory.
     * @return The access token as a String.
     * @throws Exception If an error occurs during the token retrieval process.
     */
    public String getAccessToken(String clientId, String clientSecret, String tenantId) throws Exception {
        // Construct the token endpoint URL
        String url = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";

        HttpClient client = HttpClient.newHttpClient();

        // Prepare the form data for the token request
        Map<String, String> data = Map.of(
            "grant_type", "client_credentials",
            "client_id", clientId,
            "client_secret", clientSecret,
            "scope", scopes
        );

        // Encode the form data
        StringBuilder form = new StringBuilder();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (form.length() > 0) form.append("&");
            form.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                .append("=")
                .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.err.println("Error: " + response.body());
            throw new RuntimeException("Failed to retrieve access token");
        }

        // Parse the response to extract the access token
        JSONObject jsonResponse = new JSONObject(response.body());
        return jsonResponse.getString("access_token");
    }

    @Override
    public ChatResponse getResponse(ExecutionContext context) {
        // Create a new ChatResponse object
        ChatResponse resp = new ChatResponse();
        ArrayNode resultsArray = mapper.createArrayNode();

        // Extract knowledge base data and user query
        Knowledge kb = context.getKnowledge();
        String data = kb.getData();
        SharePointConfig config = getConfig(data);

        try {
            // Retrieve the access token
            String token = getAccessToken(config.getClientId(), config.getClientSecret(), config.getTenantId());
            String queryText = context.getUsersQuery();

            HttpClient client = HttpClient.newHttpClient();

            // Construct the search URL with query parameters
            URI searchUri = URI.create(SEARCH_API_ENDPOINT + "?querytext=" + java.net.URLEncoder.encode(queryText, StandardCharsets.UTF_8) + "&rowlimit=10");

            // Create the HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(searchUri)
                .header("Accept", "application/json;odata=verbose")
                .header("Authorization", "Bearer " + token) // Add the access token
                .build();

            // Send the request asynchronously
            CompletableFuture<HttpResponse<String>> responseFuture =
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

            responseFuture.thenAccept(response -> {
                if (response.statusCode() == 200) {
                    // Parse the response body
                    String responseBody = response.body();
                    JsonNode jsonResponse;
                    try {
                        jsonResponse = mapper.readTree(responseBody);
                        JsonNode d = jsonResponse.path("d\\query\\PrimarySearchResults");
                        resultsArray.add(d);
                        saveStepData(context, resultsArray);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    // Log the error if the response status is not 200
                    System.err.println("Error: " + response.statusCode());
                    System.err.println(response.body());
                }
            }).join(); // Wait for the asynchronous request to complete

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Set the results array in the response
        resp.setData(resultsArray);
        return resp;
    }

    /**
     * Parses the SharePoint configuration from the provided data.
     *
     * @param data The JSON string containing the SharePoint configuration.
     * @return A SharePointConfig object.
     */
    private SharePointConfig getConfig(String data) {
        SharePointConfig config = null;
        try {
            if (data != null && !data.isBlank())
                config = mapper.readValue(data, SharePointConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize SharePoint configuration: " + e.getMessage());
        }
        if (config == null)
            config = sharepointConfig;
        return config;
    }

    /**
     * Downloads and extracts text content from a SharePoint file.
     *
     * @param fileUrl The URL of the SharePoint file.
     * @return The text content of the file.
     */
    private String getTextFromSharePointFile(String fileUrl) {
        String text = "No text available";
        try {
            // Handle the exception thrown by the URL constructor
            URL url = new URL(fileUrl);
            URLConnection connection = url.openConnection();
            // Add the access token to the request header
            // connection.setRequestProperty("Authorization", "Bearer " + accessToken); // Add this line
            InputStream inputStream = connection.getInputStream();
            text = readTextFromStream(inputStream);
            inputStream.close();

        } catch (Exception e) {
            e.printStackTrace();
            text = "Error occurred while downloading or reading the file:" + e.getMessage();
        }
        return text;
    }

    /**
     * Reads text content from an InputStream.
     *
     * @param inputStream The InputStream to read from.
     * @return The text content as a String.
     * @throws IOException If an error occurs during reading.
     */
    private String readTextFromStream(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder text = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            text.append(line);
            text.append("\n"); // Add newline to preserve formatting
        }
        reader.close();
        return text.toString();
    }

    private void test(ExecutionContext context, ObjectMapper mapper, ArrayNode resultsArray, SharePointConfig config) {
		try {
             GraphServiceClient<Request> client = getGraphApiClient(config.getClientId(),config.getClientSecret(),config.getTenantId());
             var results = client.sites()
                     .byId(config.getSiteId()) // Replace with your site ID
                     .drive()
                     .root()
                     .search(new DriveItemSearchParameterSet().newBuilder()
                             .withQ(context.getUsersQuery())
                             .build())
                     .buildRequest()
                     .get();

             for (DriveItem item : results.getCurrentPage()) {
                 if (item.webUrl != null) {
                     ObjectNode resultNode = mapper.createObjectNode();
                     resultNode.put("url", item.webUrl);
                     String fileContent = getTextFromSharePointFile(item.webUrl); // Download file content
                     resultNode.put("summary", fileContent);
                     resultsArray.add(resultNode);
                 }
             }
         } catch (Exception e) {
             e.printStackTrace();
             ObjectNode errorNode = mapper.createObjectNode();
             errorNode.put("error", "An error occurred during the search: " + e.getMessage());
             resultsArray.add(errorNode);

         }
	}
	/**
	 * Creates a GraphServiceClient instance for interacting with Microsoft Graph API.
	 *
	 * @param clientId     The client ID of the Azure AD application.
	 * @param clientSecret The client secret of the Azure AD application.
	 * @param tenantId     The tenant ID of the Azure AD directory.
	 * @return A configured GraphServiceClient instance.
	 * @throws RuntimeException If any of the required parameters are invalid.
	 */
	public GraphServiceClient<Request> getGraphApiClient(String clientId, String clientSecret, String tenantId) {
        // Create a GraphServiceClient instance using the provided credentials
        // and the default scopes for Microsoft Graph API
		String error = null;
        if(clientId==null || clientId.isEmpty()) {
        	error="clientId ";
        }
        if(clientSecret==null || clientSecret.isEmpty()) {
        	error+="clientSecret ";
        }   
        if(tenantId==null || tenantId.isEmpty()) {
        	error+="tenantId ";
        }
        if(error!=null)
        	throw new RuntimeException("Invlid SharePoint configuration: " +error);
		ClientSecretCredential credential = new ClientSecretCredentialBuilder() .clientId(clientId).tenantId(tenantId).clientSecret(clientSecret) .build();
        final TokenCredentialAuthProvider authProvider = new TokenCredentialAuthProvider(List.of(scopes), credential );
        return GraphServiceClient.builder().authenticationProvider(authProvider).buildClient();
	}
}