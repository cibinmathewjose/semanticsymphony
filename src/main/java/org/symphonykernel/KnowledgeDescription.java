package org.symphonykernel;
import java.util.List;

import com.azure.search.documents.indexes.SearchableField;
import com.azure.search.documents.indexes.SimpleField;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;


public class KnowledgeDescription  {

	@JsonProperty("name")
	@SearchableField( isKey = true)
	public String name;	 
	
	@JsonProperty("desc")
    @SearchableField(analyzerName = "en.microsoft")
	public String desc;	
	
	//@JsonProperty("content_vector")
	//@SearchableField
    //private List<Float> contentVector;
}
