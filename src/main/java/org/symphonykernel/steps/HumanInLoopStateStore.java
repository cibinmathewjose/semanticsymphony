package org.symphonykernel.steps;

import java.util.concurrent.CompletableFuture;

/**
 * Abstraction for storing and retrieving pending Human-In-Loop state.
 *
 * <p>This allows different backends (in-memory, Redis, DB, etc.) to be plugged in
 * without changing flow orchestration code.</p>
 */
public interface HumanInLoopStateStore {

    /**
     * Registers a pending input request.
     *
     * @param requestId request/correlation id
     * @param question question presented to the user
     * @param future future to complete when input arrives
     */
    void putPending(String requestId, String sessionId, String question, CompletableFuture<String> future);

    /**
     * Submits input for a pending request.
     *
     * @param requestId request/correlation id
     * @param userInput user answer
     * @return true if accepted, false otherwise
     */
    boolean submitInput(String requestId, String userInput);

    /**
     * Returns pending question for request, if any.
     *
     * @param requestId request/correlation id
     * @return question or null
     */
    String getPendingQuestion(String requestId);

    /**
     * Checks whether request is waiting for user input.
     *
     * @param requestId request/correlation id
     * @return true if waiting
     */
    boolean isAwaitingInput(String requestId);

    /**
     * Finds the pending request id currently awaiting user input for a session.
     *
     * @param sessionId session id
     * @return pending request id or null
     */
    String getPendingRequestIdBySession(String sessionId);

    /**
     * Consumes submitted user input for a pending request.
     *
     * @param requestId request/correlation id
     * @return submitted input if present, otherwise null
     */
    String consumeSubmittedInput(String requestId);

    /**
     * Clears pending state for request.
     *
     * @param requestId request/correlation id
     */
    void clearPending(String requestId);
}
