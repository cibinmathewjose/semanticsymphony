package org.symphonykernel.providers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.symphonykernel.core.IHttpHeaderProvider;

public class HttpHeaderProvider implements IHttpHeaderProvider {

    HttpHeaders headers;

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
