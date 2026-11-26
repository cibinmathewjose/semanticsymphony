package org.symphonykernel.core;

import java.net.URI;
import java.time.Duration;

import org.symphonykernel.ExecutionContext;

/** Supplies per-user delegated Microsoft Graph credentials for SharePoint search. */
@FunctionalInterface
public interface SharePointTokenProvider {

    /**
     * Returns a Graph bearer token for the authenticated caller, or null if not connected.
     * The host must bind the caller using trusted server-side identity, resolve the token
     * within the supplied timeout and never substitute an application or another user's token.
     * Do not read credentials or identity from caller-controlled workflow inputs.
     *
     * @param scope configured SharePoint site or folder URL
     * @param context trusted host execution context for the current request
     * @param timeout time budget for token retrieval
     * @return delegated token, without the Bearer prefix, or null
     */
    String accessToken(URI scope, ExecutionContext context, Duration timeout);
}