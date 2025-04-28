package org.symphonykernel;

import java.util.Date;

import com.azure.search.documents.indexes.SearchableField;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Index {
	
	@JsonProperty("indexName")
	@SearchableField( isKey = true)
	public String indexName;
	
	@JsonProperty("lastRefreshDate")
	public Date lastRefreshDate;
	
	@JsonProperty("fieldNames")
	public String fieldNames;
	
	@JsonProperty("indexingStatus")
	public String indexingStatus;

}
