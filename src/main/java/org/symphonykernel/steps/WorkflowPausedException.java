package org.symphonykernel.steps;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Internal control-flow exception used to stop workflow execution when user input is required.
 */
public class WorkflowPausedException extends RuntimeException {

    private final transient ObjectNode awaitingInputNode;

    public WorkflowPausedException(ObjectNode awaitingInputNode) {
        super("Workflow paused awaiting user input");
        this.awaitingInputNode = awaitingInputNode;
    }

    public ObjectNode getAwaitingInputNode() {
        return awaitingInputNode;
    }
}
