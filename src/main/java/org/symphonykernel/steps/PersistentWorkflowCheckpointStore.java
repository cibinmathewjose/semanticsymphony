package org.symphonykernel.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.symphonykernel.UserSessionStepDetails;
import org.symphonykernel.core.IUserSessionBase;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Persists workflow checkpoints via request step details table.
 */
@Service
public class PersistentWorkflowCheckpointStore implements WorkflowCheckpointStore {

    private static final Logger logger = LoggerFactory.getLogger(PersistentWorkflowCheckpointStore.class);
    private static final String CHECKPOINT_STEP_NAME = "__WORKFLOW_CHECKPOINT__";

    @Autowired
    private IUserSessionBase sessionBase;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void save(WorkflowCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.getRequestId() == null || checkpoint.getRequestId().isBlank()) {
            return;
        }
        try {
            String data = objectMapper.writeValueAsString(checkpoint);
            sessionBase.saveRequestDetails(checkpoint.getRequestId(), CHECKPOINT_STEP_NAME, data);
        } catch (Exception ex) {
            logger.error("Failed to persist workflow checkpoint for requestId={}", checkpoint.getRequestId(), ex);
        }
    }

    @Override
    public WorkflowCheckpoint load(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        try {
            UserSessionStepDetails detail = sessionBase.getRequestDetails(requestId, CHECKPOINT_STEP_NAME);
            if (detail == null || detail.getData() == null || detail.getData().isBlank()) {
                return null;
            }
            return objectMapper.readValue(detail.getData(), WorkflowCheckpoint.class);
        } catch (Exception ex) {
            logger.debug("No workflow checkpoint found for requestId={}", requestId);
            return null;
        }
    }

    @Override
    public void delete(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        try {
            sessionBase.saveRequestDetails(requestId, CHECKPOINT_STEP_NAME, "");
        } catch (Exception ex) {
            logger.error("Failed to clear workflow checkpoint for requestId={}", requestId, ex);
        }
    }
}
