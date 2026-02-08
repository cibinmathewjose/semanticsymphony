package org.symphonykernel.steps;

import java.lang.reflect.InvocationTargetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.FlowItem;
import org.symphonykernel.Knowledge;
import org.symphonykernel.LLMRequest;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.IPluginLoader;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.transformer.TemplateResolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import reactor.core.publisher.Flux;

@Component
public class PluginStep extends BaseStep {

    private static final Logger logger = LoggerFactory.getLogger(PluginStep.class);


    @Autowired
    IPluginLoader pluginLoader;

    @Autowired
    IAIClient azureOpenAIHelper;


    @Autowired
    TemplateResolver templateResolver;

    @Autowired
    IknowledgeBase knowledgeBase;

    public PluginStep() {

    }
    @Override
    public Flux<String> getResponseStream(ExecutionContext context) {
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
			  StringBuilder responseAccumulator = new StringBuilder();
			try {
				tool = pluginLoader.createObject(plugin);
				 return azureOpenAIHelper.streamExecute(new LLMRequest(systemPrompt + params, context.getUsersQuery(), new Object[]{tool}, context.getModelName())).doOnNext(responseAccumulator::append) // Capture each chunk as it flies by
	            		 .doFinally(signalType -> {
	                    	 saveStepData(context, responseAccumulator.toString());
	                     }); 
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			
		   
	}
    @Override
  	protected ArrayNode getData(ExecutionContext context) {
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
		try {
			tool = pluginLoader.createObject(plugin);
			  String msg = azureOpenAIHelper.execute(new LLMRequest(systemPrompt + params, context.getUsersQuery(), new Object[]{tool}, context.getModelName()));
		         JsonNode jsonNode = objectMapper.readTree(msg);

		         ArrayNode jsonArray;
		         if (!jsonNode.isArray()) {
		             jsonArray = objectMapper.createArrayNode();

		             jsonArray.add(jsonNode);

		         } else {
		             jsonArray = (ArrayNode) jsonNode;
		         }
		         return jsonArray;
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException
				| NoSuchMethodException | JsonProcessingException e) {
			throw new RuntimeException(e);
		}
       
    }
    @Override
    public ChatResponse getResponse(ExecutionContext context) {

       
       
        ChatResponse a = new ChatResponse();
        try {
        	ArrayNode jsonArray=getData(context);
            saveStepData(context, jsonArray);
            a.setData(jsonArray);
        } catch (Exception e) {
            logger.error("Error processing plugin step.", e);
            a.setMessage(e.getMessage());
        } 
        return a;
    }
}
