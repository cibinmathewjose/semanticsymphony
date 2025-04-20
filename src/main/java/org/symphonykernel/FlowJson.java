package org.symphonykernel;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FlowJson {
	public FlowJson()
	{
		
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
