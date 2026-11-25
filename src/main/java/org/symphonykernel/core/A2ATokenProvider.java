package org.symphonykernel.core;

import java.net.URI;

import org.symphonykernel.ExecutionContext;

/** Host-provided credentials for outbound A2A calls. */
@FunctionalInterface
public interface A2ATokenProvider {
    /**
     * Returns an access token for the authenticated caller and target audience.
     * Implementations must derive identity from trusted host authentication, not workflow inputs.
     * The timeout is the remaining budget for acquiring the token.
     *
     * @param target configured target alias
     * @param endpoint trusted target URI
     * @param context current Symphony execution context
     * @param timeout remaining call budget
     * @return raw bearer token, without a prefix
     */
    String accessToken(String target, URI endpoint, ExecutionContext context, java.time.Duration timeout);
}