package org.symphonykernel.core;

import org.springframework.http.HttpHeaders;

public interface IHttpHeaderProvider {

    HttpHeaders getHeader();

    void add(String key, String Value);
}
