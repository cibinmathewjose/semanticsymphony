package org.symphonykernel.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.FlowItem;
import org.symphonykernel.Knowledge;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.IPluginLoader;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.transformer.TemplateResolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

@Component
public class PluginStep extends BaseStep {

    private static final Logger logger = LoggerFactory.getLogger(PluginStep.class);

    private static final String PROMPT = "PROMPT";

    @Autowired
    IPluginLoader pluginLoader;

    @Autowired
    IAIClient azureOpenAIHelper;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TemplateResolver templateResolver;

    @Autowired
    IknowledgeBase knowledgeBase;

    public PluginStep() {

    }

    @Override
    public ChatResponse getResponse(ExecutionContext context) {

        ChatResponse a = new ChatResponse();
        Knowledge kb = context.getKnowledge();
        if (kb == null && context.getName() != null) {
            kb = knowledgeBase.GetByName(context.getName());
            context.setKnowledge(kb);
        }
        logger.info("Executing Plugin " + context.getKnowledge().getName());
        JsonNode paramNode = getParamNode(context.getKnowledge().getData());
        String plugin = paramNode.get("Tool").asText();

        String systemPrompt = null;
        if (paramNode.has("SystemPrompt")) {
            systemPrompt = paramNode.get("SystemPrompt").asText();
        }
        FlowItem item = context.getCurrentFlowItem();
        if (item != null && item.SystemPrompt != null) {
            systemPrompt = item.SystemPrompt;
        }

        logger.info("Parsed Plugin: " + plugin);
        if (systemPrompt != null) {
            systemPrompt = templateResolver.resolvePlaceholders(systemPrompt, context.getResolvedValues());
        }
        String params = ". Consider context parameters of first priority as " + context.getVariables() + " and second priority as " + context.getResolvedValues();

        Object tool;
        String msg = "";
        try {
            tool = pluginLoader.createObject(plugin);
            msg = azureOpenAIHelper.execute(systemPrompt + params, context.getUsersQuery(), new Object[]{tool}, context.getModelName());
            JsonNode jsonNode = objectMapper.readTree(msg);

            ArrayNode jsonArray;
            if (!jsonNode.isArray()) {
                jsonArray = objectMapper.createArrayNode();

                jsonArray.add(jsonNode);

            } else {
                jsonArray = (ArrayNode) jsonNode;
            }
            saveStepData(context, jsonArray);
            a.setData(jsonArray);
        } catch (JsonProcessingException e) {
            logger.info("Message is not a valid JSON, treating as plain text.");
            a.setMessage(msg);
        } catch (Exception ex) {
            logger.error("Error processing plugin step.", ex);
        }
        return a;
    }
}
