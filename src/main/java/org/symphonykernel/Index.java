package org.symphonykernel;

import java.util.Date;

import com.azure.search.documents.indexes.SearchableField;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an index with metadata such as name, refresh date, field names, and status.
 * This class is used for managing and tracking index information.
 */
public class Index {
    /**
     * The name of the index.
     */
    @JsonProperty("indexName")
    @SearchableField(isKey = true)
    public String indexName;

    /**
     * The date when the index was last refreshed.
     */
    @JsonProperty("lastRefreshDate")
    public Date lastRefreshDate;

    /**
     * The names of the fields in the index.
     */
    @JsonProperty("fieldNames")
    public String fieldNames;

    /**
     * The current status of the indexing process.
     */
    @JsonProperty("indexingStatus")
    public String indexingStatus;

}
