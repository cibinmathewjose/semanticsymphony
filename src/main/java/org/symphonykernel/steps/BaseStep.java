/*
 * The MIT License
 *
 * Copyright 2025 cjose.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.symphonykernel.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.transformer.JsonTransformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * @author cjose
 */
public abstract class BaseStep  implements IStep {

    protected static final Logger logger = LoggerFactory.getLogger(BaseStep.class);
    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    IknowledgeBase knowledgeBase;
       
    @Override
    public JsonNode executeQueryByName(ExecutionContext context) {
        final ArrayNode[] array = new ArrayNode[1];
        Knowledge kb = knowledgeBase.GetByName(context.getName());
        if (kb != null) {
            JsonNode var = context.getVariables();
            if (context.getConvert()) {
                try {
                    JsonTransformer transformer = new JsonTransformer();
                    var = transformer.compareAndReplaceJson(kb.getParams(), context.getVariables());
                    context.setVariables(var);
                    context.setKnowledge(kb);       		  
                } catch (Exception e) {
                   logger.error("Error in Json Transformation", e);
                }
            }
            ChatResponse a = getResponse(context);
            if (a != null) {
                if (a.getData() != null) {
                    array[0] = a.getData();
                } else if (a.getMessage() != null) {
                    array[0] = JsonNodeFactory.instance.arrayNode().add(createTextNode(a.getMessage()));
                }
            }
        }
        return array[0];
    }

     protected JsonNode getParamNode(String plugindef) {
        JsonNode paramNode;
        try {
            paramNode = objectMapper.readTree(plugindef);
        } catch (JsonProcessingException e) {
            logger.error("Error parsing plugin definition JSON", e);
            throw new IllegalArgumentException("Invalid plugin definition JSON", e);
        }
        return paramNode;
    }
    
    protected JsonNode createTextNode(String jsonString) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("TextOutput", jsonString);
        return objectNode;
    }
      /**
     * Parses a JSON string into a JsonNode.
     *
     * @param jsonString the JSON string to parse
     * @return the parsed JsonNode
     */
    public JsonNode parseJson(String jsonString) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            if (jsonString != null) {
                if (jsonString.startsWith("```json")) {
                    jsonString = jsonString.replace("```json", "").replace("```", "");
                }

            } else {
                jsonString = "";
            }
            if (jsonString.startsWith("[") || jsonString.startsWith("{")) {
                return objectMapper.readTree(jsonString);
            } else {
                return createTextNode(jsonString);
            }

        } catch (Exception e) {

            return createTextNode(jsonString);
        }
    }

}
