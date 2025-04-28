package org.symphonykernel.ai;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.symphonykernel.Knowledge;
import org.symphonykernel.config.AzureAISearchConnectionProperties;
import org.symphonykernel.config.SymphonyKernelAutoConfiguration;

import com.azure.core.annotation.ReturnType;
import com.azure.core.annotation.ServiceMethod;
import com.azure.core.util.Context;
import com.azure.core.util.serializer.JsonSerializer;
import com.azure.core.util.serializer.TypeReference;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import com.azure.search.documents.indexes.models.IndexDocumentsBatch;
import com.azure.search.documents.indexes.models.SearchIndex;
import com.azure.search.documents.indexes.models.SearchSuggester;
import com.azure.search.documents.models.QueryAnswer;
import com.azure.search.documents.models.QueryAnswerType;
import com.azure.search.documents.models.QueryCaption;
import com.azure.search.documents.models.QueryCaptionType;
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.models.SearchResult;
import com.azure.search.documents.models.SemanticSearchOptions;
import com.azure.search.documents.models.VectorQuery;
import com.azure.search.documents.util.AutocompletePagedIterable;
import com.azure.search.documents.util.SearchPagedIterable;
import com.fasterxml.jackson.databind.JsonNode;

import reactor.core.publisher.Mono;

import com.azure.search.documents.indexes.models.SearchField;
import com.azure.search.documents.indexes.models.SearchFieldDataType;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.Embeddings;
import com.azure.ai.openai.models.EmbeddingsOptions;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.KeyCredential;
import com.azure.core.util.Configuration;
import com.azure.core.util.Context;
import com.azure.json.JsonReader;
import com.azure.json.JsonSerializable;
import com.azure.json.JsonToken;
import com.azure.json.JsonWriter;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import com.azure.search.documents.indexes.SearchableField;
import com.azure.search.documents.indexes.SimpleField;
import com.azure.search.documents.indexes.models.AzureOpenAIModelName;
import com.azure.search.documents.indexes.models.AzureOpenAIVectorizer;
import com.azure.search.documents.indexes.models.FieldBuilderOptions;
import com.azure.search.documents.indexes.models.HnswAlgorithmConfiguration;
import com.azure.search.documents.indexes.models.IndexDocumentsBatch;
import com.azure.search.documents.indexes.models.SearchField;
import com.azure.search.documents.indexes.models.SearchFieldDataType;
import com.azure.search.documents.indexes.models.SearchIndex;
import com.azure.search.documents.indexes.models.VectorSearch;
import com.azure.search.documents.indexes.models.VectorSearchProfile;
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.models.SearchResult;
import com.azure.search.documents.models.VectorSearchOptions;
import com.azure.search.documents.models.VectorizableTextQuery;
import com.azure.search.documents.util.SearchPagedIterable;
import com.azure.search.documents.indexes.models.AzureOpenAIModelName;

@Service
public class KnowledgeVector {

	  private static final Logger LOGGER = LoggerFactory.getLogger(
			  KnowledgeVector.class);

    private final AzureAISearchConnectionProperties aisearchProps;

    public KnowledgeVector(AzureAISearchConnectionProperties connectionProperties) {
        this.aisearchProps = connectionProperties;
    }
    
    public SearchClient createSearchClient(String indexName) {
    	 return new SearchClientBuilder()
                .endpoint(aisearchProps.getEndpoint())
                .credential(aisearchProps.getAzureKeyCredential())
                .indexName(indexName)
                .buildClient();
    }
    
    public <T> void createIndex(String indexName, Class<T> modelClass,List<T> data)
    {
    	createIndex(indexName, modelClass);     
    	        
        indexDocuments(indexName, data);
    }

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
    public <T> void indexDocument(String indexName, T data) {
	    List<T> list =new ArrayList<T>();
	    list.add(data);
	    indexDocuments(indexName,list);
    }

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
    public void search(String indexName,String text)
    {
    	SearchOptions options = new SearchOptions();
        options.setIncludeTotalCount(true);
        options.setFilter("");
        options.setOrderBy("");
        options.setIncludeTotalCount(true);
        options.setSelect("product_name", "brand_name", "file_name", "product_code", "content");
        options.setQueryType(com.azure.search.documents.models.QueryType.SEMANTIC);
        

	     // Create an instance of SemanticSearchOptions
	     SemanticSearchOptions semanticOptions = new SemanticSearchOptions();
	     semanticOptions.setSemanticConfigurationName("default"); // Set your semantic configuration name here
	     QueryCaption caption =new QueryCaption(QueryCaptionType.EXTRACTIVE);
	     caption.setHighlightEnabled(true);
	     semanticOptions.setQueryCaption(caption);
	     QueryAnswer answers =new QueryAnswer(QueryAnswerType.EXTRACTIVE);
	     semanticOptions.setQueryAnswer(answers);
	     // Set the semanticOptions in the SearchOptions
	     options.setSemanticSearchOptions(semanticOptions);
     
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
        options.setVectorSearchOptions(voptions);
        
    	 SearchClient searchClient = createSearchClient(indexName);    	 
    	 
         SearchPagedIterable searchResults = searchClient.search(text, options, Context.NONE);
         LOGGER.info("Count = "+searchResults.getTotalCount());
         searchResults.iterator().forEachRemaining(result ->
         {
        	 LOGGER.info(result.getDocument(JsonNode.class).toString());
         });
    }
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
    
    public <T> Iterator<T> search(String indexName, String text, SearchOptions options, Class<T> modelClass) {
        SearchClient searchClient = createSearchClient(indexName);
        SearchPagedIterable searchResults = searchClient.search(text, options, Context.NONE);
        
        return searchResults.stream()
                            .map(result -> result.getDocument(modelClass))
                            .iterator();
    }

    

}
