package org.symphonykernel.steps;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

/**
 * Default in-memory implementation for {@link HumanInLoopStateStore}.
 */
@Service
public class InMemoryHumanInLoopStateStore implements HumanInLoopStateStore {

    private static final long PENDING_INPUT_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingInputs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> submittedInputs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> pendingQuestions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> pendingCreatedAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> pendingRequestToSession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> pendingSessionToRequest = new ConcurrentHashMap<>();

    @Override
    public void putPending(String requestId, String sessionId, String question, CompletableFuture<String> future) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        if (future != null) {
            pendingInputs.put(requestId, future);
        } else {
            pendingInputs.remove(requestId);
        }
        submittedInputs.remove(requestId);
        pendingQuestions.put(requestId, question);
        pendingCreatedAt.put(requestId, System.currentTimeMillis());
        if (sessionId != null && !sessionId.isBlank()) {
            pendingRequestToSession.put(requestId, sessionId);
            pendingSessionToRequest.put(sessionId, requestId);
        }
    }

    @Override
    public boolean submitInput(String requestId, String userInput) {
        if (isExpired(requestId)) {
            clearPending(requestId);
            return false;
        }
        if (!pendingQuestions.containsKey(requestId) && !pendingInputs.containsKey(requestId)) {
            return false;
        }
        submittedInputs.put(requestId, userInput);
        CompletableFuture<String> future = pendingInputs.get(requestId);
        if (future != null) {
            future.complete(userInput);
        }
        return true;
    }

    @Override
    public String getPendingQuestion(String requestId) {
        if (isExpired(requestId)) {
            clearPending(requestId);
            return null;
        }
        return pendingQuestions.get(requestId);
    }

    @Override
    public boolean isAwaitingInput(String requestId) {
        if (isExpired(requestId)) {
            clearPending(requestId);
            return false;
        }
        return pendingQuestions.containsKey(requestId);
    }

    @Override
    public String getPendingRequestIdBySession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        String requestId = pendingSessionToRequest.get(sessionId);
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        if (isExpired(requestId)) {
            clearPending(requestId);
            return null;
        }
        return requestId;
    }

    @Override
    public String consumeSubmittedInput(String requestId) {
        if (isExpired(requestId)) {
            clearPending(requestId);
            return null;
        }
        return submittedInputs.remove(requestId);
    }

    @Override
    public void clearPending(String requestId) {
        pendingInputs.remove(requestId);
        submittedInputs.remove(requestId);
        pendingQuestions.remove(requestId);
        pendingCreatedAt.remove(requestId);
        String sessionId = pendingRequestToSession.remove(requestId);
        if (sessionId != null) {
            String mappedRequestId = pendingSessionToRequest.get(sessionId);
            if (requestId.equals(mappedRequestId)) {
                pendingSessionToRequest.remove(sessionId);
            }
        }
    }

    private boolean isExpired(String requestId) {
        if (requestId == null) {
            return true;
        }
        Long createdAt = pendingCreatedAt.get(requestId);
        if (createdAt == null) {
            return false;
        }
        return System.currentTimeMillis() - createdAt > PENDING_INPUT_TTL_MILLIS;
    }
}
