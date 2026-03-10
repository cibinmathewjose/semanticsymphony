package org.symphonykernel.agentic;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Represents an LLM-generated plan consisting of actions to execute,
 * or a final answer when the agent has completed its task.
 *
 * <p>The {@code finalAnswer} from the LLM may be a plain string or a JSON
 * array/object. After parsing, use {@link #getMessage()} for the text
 * representation and {@link #getData()} for the structured data when available.
 */
public class AgentPlan {

    @JsonProperty("actions")
    private List<AgentAction> actions;

    @JsonProperty("finalAnswer")
    private JsonNode finalAnswer;

    @JsonProperty("isComplete")
    private boolean complete;

    @JsonProperty("reasoning")
    private String reasoning;

    @JsonIgnore
    private String message;

    @JsonIgnore
    private ArrayNode data;

    /** Default constructor. */
    public AgentPlan() {
    }

    /** @return the list of planned actions */
    public List<AgentAction> getActions() {
        return actions;
    }

    /** @param actions the actions to set */
    public void setActions(List<AgentAction> actions) {
        this.actions = actions;
    }

    /** @return the raw finalAnswer JsonNode as received from the LLM */
    public JsonNode getFinalAnswer() {
        return finalAnswer;
    }

    /** @param finalAnswer the final answer node to set */
    public void setFinalAnswer(JsonNode finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    /** @return the text representation of the final answer */
    public String getMessage() {
        return message;
    }

    /** @param message the text message to set */
    public void setMessage(String message) {
        this.message = message;
    }

    /** @return structured data when the final answer is a JSON array, or null */
    public ArrayNode getData() {
        return data;
    }

    /** @param data the structured data to set */
    public void setData(ArrayNode data) {
        this.data = data;
    }

    /** @return true if the plan is complete */
    public boolean isComplete() {
        return complete;
    }

    /** @param complete whether the plan is complete */
    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    /** @return the reasoning behind this plan */
    public String getReasoning() {
        return reasoning;
    }

    /** @param reasoning the reasoning to set */
    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
}
