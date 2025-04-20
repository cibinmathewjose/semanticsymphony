package org.symphonykernel.core;

import org.symphonykernel.ExecutionContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public interface Step {
	ArrayNode getResponse(ExecutionContext context);

	JsonNode executeQueryByName(ExecutionContext context);
}
