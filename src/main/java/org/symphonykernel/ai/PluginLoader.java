package org.symphonykernel.ai;

import java.lang.reflect.InvocationTargetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.symphonykernel.core.IPluginLoader;

import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.plugin.KernelPlugin;
import com.microsoft.semantickernel.plugin.KernelPluginFactory;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
@Service
public class PluginLoader implements IPluginLoader, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(PluginLoader.class);

    private ApplicationContext applicationContext;
    
    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    /**
     * Creates an instance of a class given its fully qualified name, allowing
     * for autowired dependencies.
     *
     * @param fullyQualifiedName The fully qualified name of the class (e.g.,
     * "com.example.MyClass").
     * @return An instance of the class, or null if an error occurs.
     * @throws ClassNotFoundException if the class with the given name is not
     * found.
     * @throws InstantiationException if the class cannot be instantiated.
     * @throws IllegalAccessException if the constructor is not accessible.
     * @throws InvocationTargetException if the constructor throws an exception.
     * @throws NoSuchMethodException if an appropriate constructor is not found.
     */
    
    public Object createObject(String fullyQualifiedName) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        // Load the class using the class loader.
        Class<?> clazz = Class.forName(fullyQualifiedName);

        //Use Spring to create the instance, which will handle autowiring
        Object instance = applicationContext.getAutowireCapableBeanFactory().createBean(clazz);
        return instance;
    }
    @Override
    public Kernel load(ChatCompletionService chat,String pluginName) {
        KernelPlugin plugin;
        try {
            plugin = KernelPluginFactory.createFromObject(createObject(pluginName), pluginName);

            return Kernel.builder()
                    .withPlugin(plugin)
                    .withAIService(ChatCompletionService.class, chat)
                    .build();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException
                | NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }


}