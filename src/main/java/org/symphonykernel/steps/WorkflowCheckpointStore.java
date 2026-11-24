package org.symphonykernel.steps;

/**
 * Persists and loads workflow checkpoints for pause/resume.
 */
public interface WorkflowCheckpointStore {

    void save(WorkflowCheckpoint checkpoint);

    WorkflowCheckpoint load(String requestId);

    void delete(String requestId);
}
