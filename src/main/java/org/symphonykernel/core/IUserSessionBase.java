package org.symphonykernel.core;

import java.util.List;

import org.springframework.stereotype.Component;
import org.symphonykernel.UserSession;

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

    UserSession findById(String id);
}
