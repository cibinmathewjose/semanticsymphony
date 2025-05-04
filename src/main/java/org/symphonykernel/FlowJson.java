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

public class FlowJson {

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

    @JsonProperty("Flow")
    public List<FlowItem> Flow;
    
    @JsonProperty("SystemPrompt")
    public String SystemPrompt;    
    
    @JsonProperty("UserPrompt")
    public String UserPrompt;
    
    @JsonProperty("AdaptiveCardPrompt")
    public String AdaptiveCardPrompt;
    
    @JsonProperty("Result")
    public String Result;
}
