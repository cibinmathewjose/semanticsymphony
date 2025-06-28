package org.symphonykernel.providers;

import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.symphonykernel.ChatRequest;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.QueryType;
import org.symphonykernel.UserSession;
import org.symphonykernel.core.IUserSessionBase;

import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;

@Component
/**
 * SessionProvider is responsible for managing user sessions and chat histories.
 * <p>
 * This class provides methods to create, retrieve, and update user sessions,
 * as well as manage chat histories for user interactions.
 * </p>
 * 
 * @version 1.0
 * @since 1.0
 */
public class SessionProvider {

    private static final Logger logger = LoggerFactory.getLogger(SessionProvider.class);

    private final IUserSessionBase userSessionsBase;
    /**
     * The maximum number of chat history entries to retain.
     * This value can be configured via application properties.
     */
    @Value("${maxhistory:5}")
    private int maxHistory;

    /**
     * Constructs a SessionProvider with the specified user session base.
     *
     * @param userSessionsBase the base for managing user sessions
     */
    public SessionProvider(IUserSessionBase userSessionsBase) {
        this.userSessionsBase = userSessionsBase;
    }

    /**
     * Creates a user session based on the provided chat request.
     *
     * @param request the chat request containing session details
     * @return the created user session
     */
    public UserSession createUserSession(ChatRequest request) {
        UserSession session = new UserSession();
        session.setSessionID(request.getSession());
        session.setUserId(request.getUser());
        session.setRequestId(UUID.randomUUID().toString());
        session.setUserInput(request.getQuery());
        session.setCreateDt(Calendar.getInstance().getTime());
        session.setStatus("RECEIVED");
        return userSessionsBase.save(session);
    }

    /**
     * Retrieves the chat history for the given chat request.
     *
     * @param request the chat request for which history is to be retrieved
     * @return the chat history
     */
    public ChatHistory getChatHistory(ChatRequest request) {
        List<UserSession> sessions = userSessionsBase.getSession(request.getSession());
        ChatHistory chatHistory = new ChatHistory();
        if (sessions != null && !sessions.isEmpty()) {
            for (UserSession session : sessions) {
                if (session.getUserInput() != null && session.getBotResponse() != null) {
                    chatHistory.addUserMessage(session.getUserInput());
                    chatHistory.addSystemMessage(session.getBotResponse());
                }
            }
        }
        chatHistory.addUserMessage(request.getQuery());
        return chatHistory;
    }

    /**
     * Retrieves the session history for the specified session ID.
     *
     * @param sessionId the ID of the session
     * @return a list of user sessions
     */
    public List<UserSession> getSessionHistory(String sessionId) {
        return userSessionsBase.getSession(sessionId);
    }

    /**
     * Updates the user session with the provided response.
     *
     * @param session the user session to update
     * @param response the chat response containing update details
     */
    public void updateUserSession(UserSession session, ChatResponse response) {

        session.setBotResponse(response.getMessage());
        session.setData(response.getData() != null ? response.getData().toPrettyString() : "");

        session.setModifyDt(Calendar.getInstance().getTime());
        session.setStatus(response.getStatusCode());
        userSessionsBase.save(session);
    }
}
