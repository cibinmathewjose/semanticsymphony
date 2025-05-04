package org.symphonykernel;
import com.azure.search.documents.indexes.SearchableField;
import com.fasterxml.jackson.annotation.JsonProperty;


public class KnowledgeDescription  {

    @JsonProperty("name")
    /**
     * The name of the knowledge description.
     */
    @SearchableField( isKey = true)
    public String name;	 
    
    @JsonProperty("desc")
    @SearchableField(analyzerName = "en.microsoft")
    public String desc;	
    
    //@JsonProperty("content_vector")
    //@SearchableField
    //private List<Float> contentVector;
}
