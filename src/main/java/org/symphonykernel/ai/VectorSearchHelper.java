package org.symphonykernel.ai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.symphonykernel.config.AzureAISearchConnectionProperties;

import com.azure.core.annotation.ReturnType;
import com.azure.core.annotation.ServiceMethod;
import com.azure.core.util.Context;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import com.azure.search.documents.indexes.models.FieldBuilderOptions;
import com.azure.search.documents.indexes.models.IndexDocumentsBatch;
import com.azure.search.documents.indexes.models.SearchIndex;
import com.azure.search.documents.models.QueryAnswer;
import com.azure.search.documents.models.QueryAnswerType;
import com.azure.search.documents.models.QueryCaption;
import com.azure.search.documents.models.QueryCaptionType;
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.models.VectorQuery;
import com.azure.search.documents.models.VectorSearchOptions;
import com.azure.search.documents.models.VectorizableTextQuery;
import com.azure.search.documents.util.SearchPagedIterable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Helper class for performing vector search operations using Azure AI Search.
 * Provides methods for creating search clients, managing search indexes, indexing documents,
 * retrieving documents, and performing search operations.
 * 
 * <p>This class is annotated with {@code @Service} to indicate that it is a Spring service component.</p>
 * 
 * <p>Key functionalities include:</p>
 * <ul>
 *   <li>Creating search clients for specific indexes.</li>
 *   <li>Creating and updating search indexes based on model classes.</li>
 *   <li>Indexing single or multiple documents into a search index.</li>
 *   <li>Retrieving documents from a search index by key.</li>
 *   <li>Performing search operations with various options, including semantic and vector search.</li>
 * </ul>
 * 
 * <p>Dependencies:</p>
 * <ul>
 *   <li>Azure AI Search SDK for Java</li>
 *   <li>Jackson ObjectMapper for JSON processing</li>
 *   <li>SLF4J for logging</li>
 *   <li>Spring Framework for dependency injection</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * VectorSearchHelper helper = new VectorSearchHelper(connectionProperties, objectMapper);
 * helper.createIndex("myIndex", MyModel.class);
 * helper.indexDocument("myIndex", new MyModel());
 * MyModel document = helper.getDocument("myIndex", "documentKey", MyModel.class);
 * }</pre>
 * 
 * <p>Note: Ensure that the Azure AI Search connection properties are properly configured.</p>
 */
@Service
public class VectorSearchHelper {

	  private static final Logger LOGGER = LoggerFactory.getLogger(
			  VectorSearchHelper.class);

    private final AzureAISearchConnectionProperties aisearchProps;
    ObjectMapper objectMapper ;

    /**
     * Constructs a VectorSearchHelper with the specified connection properties and object mapper.
     *
     * @param connectionProperties the connection properties for Azure AI Search
     * @param objectMapper the object mapper for JSON processing
     */
    public VectorSearchHelper(AzureAISearchConnectionProperties connectionProperties, ObjectMapper objectMapper) {
        this.aisearchProps = connectionProperties;
        this.objectMapper=objectMapper;
    }
    
    /**
     * Creates a search client for the specified index.
     *
     * @param indexName the name of the index
     * @return the search client for the index
     */
    public SearchClient createSearchClient(String indexName) {
    	 return new SearchClientBuilder()
                .endpoint(aisearchProps.getEndpoint())
                .credential(aisearchProps.getAzureKeyCredential())
                .indexName(indexName)
                .buildClient();
    }
    //public static List<Float> getEmbeddings(String text) {
        
   // }
    /**
     * Creates an index with the specified name and model class.
     *
     * @param <T> the type of the model class
     * @param indexName the name of the index
     * @param modelClass the class of the model
     */
	public <T> void createIndex(String indexName, Class<T> modelClass) {
		SearchIndexClient searchIndexClient = new SearchIndexClientBuilder()
    	            .endpoint(aisearchProps.getEndpoint())
    	            .credential(aisearchProps.getAzureKeyCredential())
    	            .buildClient();
		FieldBuilderOptions options =new FieldBuilderOptions();
		//options.setJsonSerializer();
    	 // Create Search Index for Knowledge model
    	searchIndexClient.createOrUpdateIndex(
    				 new SearchIndex(indexName, SearchIndexClient.buildSearchFields(modelClass, options)));
	}

    /**
     * Creates an index with the specified name, model class, and initial data.
     *
     * @param <T> the type of the model class
     * @param indexName the name of the index
     * @param modelClass the class of the model
     * @param data the initial data to index
     */
    public <T> void createIndex(String indexName, Class<T> modelClass,List<T> data)
    {
    	createIndex(indexName, modelClass);     
    	        
        indexDocuments(indexName, data);
    }

    /**
     * Indexes a single document in the specified index.
     *
     * @param <T> the type of the document
     * @param indexName the name of the index
     * @param data the document to index
     */
    public <T> void indexDocument(String indexName, T data) {
	    List<T> list =new ArrayList<T>();
	    list.add(data);
	    indexDocuments(indexName,list);
    }

    /**
     * Indexes multiple documents in the specified index.
     *
     * @param <T> the type of the documents
     * @param indexName the name of the index
     * @param data the list of documents to index
     */
	public <T> void indexDocuments(String indexName, List<T> data) {
		if(data!=null)
        {
        var batch = new IndexDocumentsBatch<T>();
        batch.addMergeOrUploadActions(data);
        
        SearchClient searchClient =createSearchClient(indexName);
        try
        {
            searchClient.indexDocuments(batch);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            // If for some reason any documents are dropped during indexing, you can compensate by delaying and
            // retrying. This simple demo just logs failure and continues
            System.err.println("Failed to index some of the documents");
        }        
        }
	}

    /**
     * Retrieves a document from the specified index by its key.
     *
     * @param <T> the type of the document
     * @param indexName the name of the index
     * @param key the key of the document
     * @param modelClass the class of the document
     * @return the retrieved document
     */
    @ServiceMethod(returns = ReturnType.SINGLE)
    public <T> T getDocument(String indexName,String key, Class<T> modelClass) 
    {    
    	T lookupResponse = null;    
    	try
    	{
    	SearchClient searchClient =createSearchClient(indexName);
    	lookupResponse = searchClient.getDocument(key, modelClass);
    	}
    	catch (Exception e) {
    		 LOGGER.info("{} key {} not found in {} ",e.getMessage(),indexName,key);
		}
        return lookupResponse;
    }
    
    /**
     * Searches the specified index for documents matching the given text and options.
     *
     * @param <T> the type of the documents
     * @param indexName the name of the index
     * @param text the search text
     * @param options the search options
     * @param modelClass the class of the documents
     * @return an iterator over the search results
     */
    public <T> Iterator<T> search(String indexName, String text, SearchOptions options, Class<T> modelClass) {
        SearchClient searchClient = createSearchClient(indexName);
        SearchPagedIterable searchResults = searchClient.search(text, options, Context.NONE);
        
        return searchResults.stream()
                            .map(result -> result.getDocument(modelClass))
                            .iterator();
    }

    /**
     * Searches the specified index for documents matching the given text and fields.
     *
     * @param indexName the name of the index
     * @param text the search text
     * @param fields the fields to search in
     * @return an array node containing the search results
     */
    public ArrayNode Search(String indexName,String text,String fields)
    {
    	SearchOptions options = new SearchOptions();
        options.setIncludeTotalCount(true);
        options.setFilter("");
        options.setOrderBy("");
        options.setIncludeTotalCount(true);
        if(fields!=null)
        options.setSelect(fields);
        //options.setQueryType(com.azure.search.documents.models.QueryType.SEMANTIC);
        

	     // Create an instance of SemanticSearchOptions
	     //SemanticSearchOptions semanticOptions = new SemanticSearchOptions();
	    // semanticOptions.setSemanticConfigurationName("default"); // Set your semantic configuration name here
	     QueryCaption caption =new QueryCaption(QueryCaptionType.EXTRACTIVE);
	     caption.setHighlightEnabled(true);
	  //   semanticOptions.setQueryCaption(caption);
	     QueryAnswer answers =new QueryAnswer(QueryAnswerType.EXTRACTIVE);
	  //   semanticOptions.setQueryAnswer(answers);
	     // Set the semanticOptions in the SearchOptions
	    // options.setSemanticSearchOptions(semanticOptions);
     
        //options.setQueryLanguage("en-us");
        //options.setQueryRewrites("generative");
        options.setTop(2);
        options.setSkip(1);

        VectorSearchOptions voptions = new VectorSearchOptions();
        List<VectorQuery> vQuery = new ArrayList<>();
        VectorQuery q = new VectorizableTextQuery(text)
        		.setFields("content_vector");   
            	//.setQueryRewrites("generative");
        vQuery.add(q);
        voptions.setQueries(vQuery);
        //voptions.setVectorFilterMode("postFilter");
       // options.setVectorSearchOptions(voptions);
    	 SearchClient searchClient = createSearchClient(indexName);    	 
         SearchPagedIterable searchResults = searchClient.search(text, options, Context.NONE);
         // Convert results to JSONArray
    
        // Convert results to ArrayNode
	    
	    ArrayNode arrayNode = objectMapper.createArrayNode();
	
	    searchResults.forEach(result -> {
	        JsonNode document = result.getDocument(JsonNode.class);
	        if (document != null) {
	            arrayNode.add(document);
	        }
	    });

    return arrayNode;
        
    }

    

}
