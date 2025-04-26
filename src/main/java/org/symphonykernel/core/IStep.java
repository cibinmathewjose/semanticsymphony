package org.symphonykernel.core;

import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;

import com.fasterxml.jackson.databind.JsonNode;

public interface IStep {

    ChatResponse getResponse(ExecutionContext context);

    JsonNode executeQueryByName(ExecutionContext context);
}
