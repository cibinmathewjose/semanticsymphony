/**
 * Represents a JSON structure for a flow, containing flow items and prompts.
 * 
 * This class is used to serialize and deserialize flow-related data.
 * 
 * @author Cibin Jose
 */
package org.symphonykernel;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a JSON structure for managing flow-related data.
 */
public class FlowJson {

    /**
     * List of flow items.
     */
    @JsonProperty("Flow")
    public List<FlowItem> Flow;
    
    /**
     * System prompt for the flow.
     */
    @JsonProperty("SystemPrompt")
    public String SystemPrompt;    
    
    /**
     * User prompt for the flow.
     */
    @JsonProperty("UserPrompt")
    public String UserPrompt;
    
    /**
     * Adaptive card prompt for the flow.
     */
    @JsonProperty("AdaptiveCardPrompt")
    public String AdaptiveCardPrompt;
    
    /**
     * Result of the flow.
     */
    @JsonProperty("Result")
    public String Result;
    
    /**
     * Gets the list of flow items.
     * 
     * @return the list of flow items
     */
    public List<FlowItem> getFlow() {
        return Flow;
    }

    /**
     * Sets the list of flow items.
     * 
     * @param flow the list of flow items to set
     */
    public void setFlow(List<FlowItem> flow) {
        this.Flow = flow;
    }

}
