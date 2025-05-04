package org.symphonykernel.core;

import org.springframework.stereotype.Component;

import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;

/**
 * Interface for loading plugins into the kernel.
 */
@Component
public interface IPluginLoader {

    /**
     * Loads a plugin into the kernel.
     *
     * @param chat the chat completion service.
     * @param pluginName the name of the plugin to load.
     * @return the kernel instance with the loaded plugin.
     */
    Kernel load(ChatCompletionService chat, String pluginName);
}