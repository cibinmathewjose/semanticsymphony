package org.symphonykernel.core;

import java.lang.reflect.InvocationTargetException;

import org.springframework.stereotype.Component;

/**
 * Interface for loading plugins into the kernel.
 */
@Component
public interface IPluginLoader {

    /**
     * Creates an instance of a class given its fully qualified name, with support for Spring's
     * dependency injection.
     *
     * @param fullyQualifiedName the fully qualified name of the class to instantiate.
     * @param type the expected type of the class.
     * @param <T> the type parameter.
     * @return an instance of the specified class type.
     */
    <T> T createObject(String fullyQualifiedName, Class<T> type);
    /**
     * Creates an instance of a class given its fully qualified name, with support for Spring's
     * dependency injection.
     *
     * @param fullyQualifiedName the fully qualified name of the class to instantiate.
     * @return an instance of the specified class type.
     * @throws ClassNotFoundException if the class cannot be found.
     * @throws InstantiationException if the class cannot be instantiated.
     * @throws IllegalAccessException if the class or its nullary constructor is not accessible.
     * @throws InvocationTargetException if the underlying constructor throws an exception.
     * @throws NoSuchMethodException if an appropriate constructor is not found.
     */
    Object createObject(String fullyQualifiedName) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException;
}