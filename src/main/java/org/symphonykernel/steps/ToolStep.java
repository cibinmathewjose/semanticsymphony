package org.symphonykernel.steps;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.core.IPluginLoader;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.transformer.TemplateResolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;

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
                logger.error("Plugin of type IStep not found: " + plugin);
                throw new IllegalArgumentException("Plugin not found: " + plugin);
            }
            a= step.getResponse(context);
            saveStepData(context, a.getData());
        return a;
    }
}
