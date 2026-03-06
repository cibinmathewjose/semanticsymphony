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

    /** Default constructor. */
    public AgentAction() {
    }

    /**
     * Constructs an AgentAction with the specified parameters.
     *
     * @param tool the name of the tool to invoke
     * @param arguments the arguments to pass to the tool
     * @param reasoning the LLM's reasoning for choosing this action
     */
    public AgentAction(String tool, Map<String, Object> arguments, String reasoning) {
        this.tool = tool;
        this.arguments = arguments;
        this.reasoning = reasoning;
    }

    /** @return the tool name */
    public String getTool() {
        return tool;
    }

    /** @param tool the tool name to set */
    public void setTool(String tool) {
        this.tool = tool;
    }

    /** @return the tool arguments */
    public Map<String, Object> getArguments() {
        return arguments;
    }

    /** @param arguments the tool arguments to set */
    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    /** @return the reasoning for this action */
    public String getReasoning() {
        return reasoning;
    }

    /** @param reasoning the reasoning to set */
    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    /** @return the list of action names this action depends on */
    public List<String> getDependsOn() {
        return dependsOn;
    }

    /** @param dependsOn the dependency list to set */
    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }

    @Override
    public String toString() {
        return "AgentAction{tool='" + tool + "', reasoning='" + reasoning + "'}";
    }
}
