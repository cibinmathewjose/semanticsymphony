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

    /** @return the final answer, or null if the plan is not complete */
    public String getFinalAnswer() {
        return finalAnswer;
    }

    /** @param finalAnswer the final answer to set */
    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
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
