package org.symphonykernel.providers;

import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.symphonykernel.ChatRequest;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.UserSession;
import org.symphonykernel.core.IUserSessionBase;

import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;

@Component
public class SessionProvider {

    private static final Logger logger = LoggerFactory.getLogger(SessionProvider.class);

    private final IUserSessionBase userSessionsBase;

    public SessionProvider(IUserSessionBase userSessionsBase) {
        this.userSessionsBase = userSessionsBase;
    }

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
    

    public List<UserSession> getSessionHistory(String sessionId) {
        return userSessionsBase.getSession(sessionId);
    }

    public void updateUserSession(UserSession session, ChatResponse response) {

        session.setBotResponse(response.getMessage());
        session.setData(response.getData() != null ? response.getData().toPrettyString() : "");

        session.setModifyDt(Calendar.getInstance().getTime());
        session.setStatus(response.getStatusCode());
        userSessionsBase.save(session);
    }
}
