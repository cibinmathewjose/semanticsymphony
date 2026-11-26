package org.symphonykernel.steps;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Minimal persisted state required to resume a paused workflow.
 */
public class WorkflowCheckpoint {

    private String requestId;
    private String generation;
    private String knowledgeName;
    private String waitingStepKey;
    private String modelName;
    private Map<String, JsonNode> resolvedValues = new HashMap<>();

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getGeneration() {
        return generation;
    }

    public void setGeneration(String generation) {
        this.generation = generation;
    }

    public String getKnowledgeName() {
        return knowledgeName;
    }

    public void setKnowledgeName(String knowledgeName) {
        this.knowledgeName = knowledgeName;
    }

    public String getWaitingStepKey() {
        return waitingStepKey;
    }

    public void setWaitingStepKey(String waitingStepKey) {
        this.waitingStepKey = waitingStepKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Map<String, JsonNode> getResolvedValues() {
        return resolvedValues;
    }

    public void setResolvedValues(Map<String, JsonNode> resolvedValues) {
        this.resolvedValues = resolvedValues != null ? resolvedValues : new HashMap<>();
    }
}
