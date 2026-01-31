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

/**
 * The PluginLoader class is a Spring-managed service that implements the {@link IPluginLoader} 
 * interface and the {@link ApplicationContextAware} interface. It is responsible for loading 
 * plugins into the Semantic Kernel and creating instances of classes with support for Spring's 
 * dependency injection.
 *
 * <p>Key Responsibilities:
 * <ul>
 *   <li>Provides a method to create an instance of a class given its fully qualified name, 
 *       leveraging Spring's autowiring capabilities.</li>
 *   <li>Implements the {@code load} method to load a plugin into the Semantic Kernel, 
 *       integrating it with a specified {@link ChatCompletionService}.</li>
 * </ul>
 *
 * <p>Dependencies:
 * <ul>
 *   <li>{@link ApplicationContext} - Used to access Spring's bean factory for creating instances.</li>
 *   <li>{@link KernelPluginFactory} - Used to create a {@link KernelPlugin} from an object.</li>
 *   <li>{@link Kernel} - Represents the Semantic Kernel to which plugins are added.</li>
 *   <li>{@link ChatCompletionService} - AI service used by the kernel.</li>
 * </ul>
 *
 * <p>Exception Handling:
 * <ul>
 *   <li>Handles various exceptions such as {@link ClassNotFoundException}, 
 *       {@link InstantiationException}, {@link IllegalAccessException}, 
 *       {@link InvocationTargetException}, and {@link NoSuchMethodException} 
 *       during object creation and plugin loading.</li>
 *   <li>Logs errors and returns {@code null} if plugin loading fails.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * {@code
 * PluginLoader pluginLoader = new PluginLoader();
 * Kernel kernel = pluginLoader.load(chatCompletionService, "com.example.MyPlugin");
 * }
 * </pre>
 */
/**
 * Implementation of the {@link IPluginLoader} interface for loading plugins into the kernel.
 */
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
     */
    public Object createObject(String fullyQualifiedName) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        // Load the class using the class loader.
        Class<?> clazz = Class.forName(fullyQualifiedName);

        //Use Spring to create the instance, which will handle autowiring
        Object instance = applicationContext.getAutowireCapableBeanFactory().createBean(clazz);
        return instance;
    }
    /**
     * Creates an instance of a class given its fully qualified name and casts it to the specified type.
     *
     * @param <T> The type to cast the created object to.
     * @param fullyQualifiedName The fully qualified name of the class (e.g., "com.example.MyClass").
     * @param type The Class object representing the type to cast to.
     * @return An instance of the class cast to the specified type, or null if an error occurs.
     */
    public <T> T createObject(String fullyQualifiedName, Class<T> type)  {
     
        // Use Spring to create the instance, which will handle autowiring
        Object instance;
        try {
            instance = createObject(fullyQualifiedName);

            // Cast the instance to the specified type
            return type.cast(instance);
        } catch (Exception e) {
            logger.error("Error creating instance of class: " + fullyQualifiedName, e);
            return null;
        }      
    }

}