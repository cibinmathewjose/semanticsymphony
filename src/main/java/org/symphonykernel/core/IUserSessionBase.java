package org.symphonykernel.core;

import java.util.List;

import org.springframework.stereotype.Component;
import org.symphonykernel.UserSession;

@Component
public interface IUserSessionBase {

    List<UserSession> getSession(String id);

    UserSession save(UserSession session);
}
