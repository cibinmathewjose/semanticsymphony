package org.symphonykernel.core;

import org.springframework.http.HttpHeaders;

/**
 * Interface for defining methods to manage HTTP headers.
 *
 * This interface provides methods to retrieve and add HTTP headers, enabling
 * customization and management of HTTP requests.
 */
public interface IHttpHeaderProvider {

    /**
     * Retrieves the HTTP headers.
     *
     * @return the HTTP headers
     */
    HttpHeaders getHeader();

    /**
     * Adds a key-value pair to the HTTP headers.
     *
     * @param key   the header key
     * @param value the header value
     */
    void add(String key, String value);
}
