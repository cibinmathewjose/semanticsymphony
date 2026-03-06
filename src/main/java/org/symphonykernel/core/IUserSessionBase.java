package org.symphonykernel.core;

import java.util.List;

import org.springframework.stereotype.Component;
import org.symphonykernel.UserSession;
import org.symphonykernel.UserSessionStepDetails;

/**
 * IUserSessionBase defines the contract for managing user sessions.
 * It provides methods to retrieve and save user session data.
 */
@Component
public interface IUserSessionBase {

    /**
     * Retrieves a list of user sessions associated with the given ID.
     *
     * @param id the identifier for the user sessions
     * @return a list of UserSession objects
     */
    List<UserSession> getSession(String id);

    /**
     * Saves a user session to the data store.
     *
     * @param session the UserSession object to save
     * @return the saved UserSession object
     */
    UserSession save(UserSession session);

    /**
     * Finds a user session by ID.
     *
     * @param id the session ID
     * @return the user session
     */
    UserSession findById(String id);

    /**
     * Gets the last request ID for a session.
     *
     * @param sessionId the session ID
     * @return the last request ID
     */
    String getLastRequestId(String sessionId);
    /**
     * Gets all step details for a request.
     *
     * @param id the request ID
     * @return the list of step details
     */
    List<UserSessionStepDetails> getRequestDetails(String id);
    /**
     * Gets step details for a specific step.
     *
     * @param id the request ID
     * @param stepName the step name
     * @return the step details
     */
    UserSessionStepDetails getRequestDetails(String id,String stepName);
    /**
     * Saves step details for a request.
     *
     * @param id the request ID
     * @param stepName the step name
     * @param data the data to save
     */
    void saveRequestDetails(String id,String stepName,String data);
    /**
     * Gets the follow-up details for a request.
     *
     * @param id the request ID
     * @return the follow-up step details
     */
    UserSessionStepDetails getFollowUpDetails(String id);
}
