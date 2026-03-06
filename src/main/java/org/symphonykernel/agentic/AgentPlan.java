package org.symphonykernel.agentic;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an LLM-generated plan consisting of actions to execute,
 * or a final answer when the agent has completed its task.
 */
public class AgentPlan {

    @JsonProperty("actions")
    private List<AgentAction> actions;

    @JsonProperty("finalAnswer")
    private String finalAnswer;

    @JsonProperty("isComplete")
    private boolean complete;

    @JsonProperty("reasoning")
    private String reasoning;

    public AgentPlan() {
    }

    public List<AgentAction> getActions() {
        return actions;
    }

    public void setActions(List<AgentAction> actions) {
        this.actions = actions;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
}
