package org.symphonykernel.core;

import org.springframework.stereotype.Component;

import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
@Component
public interface IPluginLoader {

   Kernel load(ChatCompletionService chat,String pluginName);

}