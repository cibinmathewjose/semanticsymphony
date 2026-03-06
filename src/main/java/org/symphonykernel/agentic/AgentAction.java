package org.symphonykernel.agentic;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single step in an agent-generated plan.
 * The LLM produces these to describe what tool to call and with what arguments.
 */
public class AgentAction {

    @JsonProperty("tool")
    private String tool;

    @JsonProperty("arguments")
    private Map<String, Object> arguments;

    @JsonProperty("reasoning")
    private String reasoning;

    @JsonProperty("dependsOn")
    private List<String> dependsOn;

    public AgentAction() {
    }

    public AgentAction(String tool, Map<String, Object> arguments, String reasoning) {
        this.tool = tool;
        this.arguments = arguments;
        this.reasoning = reasoning;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public List<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }

    @Override
    public String toString() {
        return "AgentAction{tool='" + tool + "', reasoning='" + reasoning + "'}";
    }
}
