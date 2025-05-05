package org.symphonykernel.providers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.symphonykernel.core.IHttpHeaderProvider;

/**
 * Provides implementation for managing HTTP headers.
 * Implements {@link IHttpHeaderProvider}.
 */
public class HttpHeaderProvider implements IHttpHeaderProvider {

    HttpHeaders headers;

    /**
     * Default constructor for HttpHeaderProvider.
     */
    public HttpHeaderProvider() {
        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

    }

    @Override
    public HttpHeaders getHeader() {

        return headers;
    }

    @Override
    public void add(String key, String Value) {
        headers.add(key, Value);

    }
}
