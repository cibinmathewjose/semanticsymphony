package org.symphonykernel;
import com.azure.search.documents.indexes.SearchableField;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * KnowledgeDescription represents metadata about a knowledge entity,
 * including its name and description for indexing and search purposes.
 */
public class KnowledgeDescription  {

    /**
     * The name of the knowledge entity.
     * This field is marked as the key for search indexing.
     */
    @JsonProperty("name")
    @SearchableField(isKey = true)
    public String name;

    /**
     * A description of the knowledge entity.
     * This field uses the "en.microsoft" analyzer for search indexing.
     */
    @JsonProperty("desc")
    @SearchableField(analyzerName = "en.microsoft")
    public String desc;	
    
    //@JsonProperty("content_vector")
    //@SearchableField
    //private List<Float> contentVector;
}
