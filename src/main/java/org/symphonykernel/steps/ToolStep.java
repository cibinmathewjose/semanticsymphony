package org.symphonykernel.steps;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.FlowItem;
import org.symphonykernel.Knowledge;
import org.symphonykernel.ai.AzureOpenAIHelper;
import org.symphonykernel.config.AzureOpenAIConnectionProperties;
import org.symphonykernel.core.IPluginLoader;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.transformer.TemplateResolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion;
import com.microsoft.semantickernel.implementation.CollectionUtil;
import com.microsoft.semantickernel.orchestration.InvocationContext;
import com.microsoft.semantickernel.orchestration.ToolCallBehavior;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;
import com.microsoft.semantickernel.services.chatcompletion.ChatMessageContent;

/**
 * PluginStep is responsible for executing plugins using Azure OpenAI services.
 * It integrates with the Symphony Kernel to process chat responses and queries.
 */
@Component
public class ToolStep extends BaseStep {



    @Autowired
    IPluginLoader pluginLoader;


    @Autowired
    TemplateResolver templateResolver;
    
    @Autowired
    IknowledgeBase knowledgeBase;

    ChatCompletionService chat;

    @Override
    public  ChatResponse getResponse(ExecutionContext context) {
    	
         ChatResponse a = new ChatResponse();  
        Knowledge kb= context.getKnowledge();
        if( kb == null&&context.getName()!=null) {
            kb = knowledgeBase.GetByName(context.getName());
            context.setKnowledge(kb);
        }
        logger.info("Executing Tool " + context.getKnowledge().getName() );
        JsonNode paramNode = getParamNode( context.getKnowledge().getData());
        String plugin = paramNode.get("Tool").asText();
        
            IStep step = pluginLoader.createObject(plugin, IStep.class);
            if (step == null) {
                logger.error("Plugin not found: " + plugin);
                throw new IllegalArgumentException("Plugin not found: " + plugin);
            }
            a= step.getResponse(context);
            saveStepData(context, a.getData());
        return a;
    }
}
