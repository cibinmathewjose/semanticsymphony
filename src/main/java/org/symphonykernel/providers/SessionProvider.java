package org.symphonykernel.providers;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.symphonykernel.ChatRequest;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.UserSession;
import org.symphonykernel.UserSessionStepDetails;
import org.symphonykernel.core.IUserSessionBase;

import com.fasterxml.jackson.databind.ObjectMapper;
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
/**
 * Provides methods to manage user sessions and chat histories.
 * This includes creating, retrieving, and updating user sessions.
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        
        session.setRequestId(request.getConversationId() != null ? request.getConversationId() : UUID.randomUUID().toString());       
        try {
            session.setUserInput(objectMapper.writeValueAsString(new Object() {
                public String key = request.getKey();
                public String query = request.getQuery();
                public String payload = request.getPayload();
                public Map<String, String> context = request.getContextInfo();
            }));
        } catch (Exception e) {
            logger.error("Failed to convert ChatRequest to JSON", e);
            session.setUserInput("{}");
        }
        session.setCreateDt(Calendar.getInstance().getTime());
        session.setStatus("RECEIVED");
        return userSessionsBase.save(session);
    }

    /**
     * Retrieves the chat history for the given chat request.
     *
     * @param request the chat request for which history is to be retrieved
     * @return the chat history containing user and system messages
     */
    public ChatHistory getChatHistory(ChatRequest request) {
        List<UserSession> sessions = userSessionsBase.getSession(request.getSession());
        ChatHistory chatHistory = new ChatHistory();
        if (sessions != null && !sessions.isEmpty()) {
            int start = Math.max(0, sessions.size() - maxHistory);
            for (UserSession session : sessions.subList(start, sessions.size())) {
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
     * Retrieves the details of a user session based on the request ID.
     *
     * @param requestId the ID of the request
     * @return the user session details, or null if the request ID is null
     */
    public UserSession getRequest(String requestId) {
        if(requestId == null)
            return null;
        return userSessionsBase.findById(requestId);
    }

    public List<UserSessionStepDetails> getRequestDetails(String requestId) {
        if(requestId == null)
            return null;
        return userSessionsBase.getRequestDetails(requestId);
    }
    public UserSessionStepDetails getFollowUpDetails(String requestId) {
        if(requestId == null)
            return null;
        return userSessionsBase.getFollowUpDetails(requestId);
    }
    public String getLastRequestId(String sessionId) {
        if(sessionId == null)
            return null;
        return userSessionsBase.getLastRequestId(sessionId);
    }

    /**
     * Updates the user session with the provided response.
     *
     * @param session the user session to update
     * @param response the chat response containing update details
     */
    public void updateUserSession(UserSession session, ChatResponse response) {

        if(response!=null)
        {
            session.setBotResponse(response.getMessage());
            session.setData(response.getData() != null ? response.getData().toPrettyString() : "");
            session.setStatus(response.getStatusCode());
        }

        session.setModifyDt(Calendar.getInstance().getTime());
        userSessionsBase.save(session);
    }
    public void updateUserSession(UserSession session, String response,String status) {

        if(response!=null)
        {
            session.setBotResponse(response);         
            session.setStatus(status);
        }
        session.setModifyDt(Calendar.getInstance().getTime());
        userSessionsBase.save(session);
    }
    /**
     * Updates the user session with the provided response.
     *
     * @param session the user session to update
     */
    public void updateUserSession(UserSession session) {
        session.setModifyDt(Calendar.getInstance().getTime());
        userSessionsBase.save(session);
    }
    public void saveRequestDetails(String id,String stepName,String data) {
		if(stepName==null || id==null|| data==null)
			return;
		userSessionsBase.saveRequestDetails(id, stepName, data);
	}
}
