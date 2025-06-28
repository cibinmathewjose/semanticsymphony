package org.symphonykernel.core;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.symphonykernel.Knowledge;
import org.symphonykernel.KnowledgeDescription;

/**
 * Interface for managing and retrieving knowledge descriptions and views.
 * Provides methods to fetch knowledge by name, retrieve view definitions, and
 * access active and all knowledge descriptions.
 */
@Component
public interface IknowledgeBase extends IDelta<KnowledgeDescription> {

    /**
     * Retrieves a knowledge entity by its name.
     *
     * @param name the name of the knowledge entity to retrieve
     * @return the knowledge entity
     */
    Knowledge GetByName(String name);    

    /**
     * Retrieves the view definition by its name.
     *
     * @param name the name of the view definition to retrieve
     * @return the view definition as a string
     */
    String GetViewDefByName(String name);

    /**
     * Retrieves all knowledge descriptions.
     *
     * @return a map of knowledge descriptions with their names as keys
     */
    Map<String, String> getAllKnowledgeDescriptions();

    /**
     * Retrieves active knowledge descriptions.
     *
     * @return a map of active knowledge descriptions with their names as keys
     */
    Map<String, String> getActiveKnowledgeDescriptions();

    /**
     * Retrieves all view descriptions.
     *
     * @return a map of view descriptions with their names as keys
     */
    Map<String, String> getAllVewDescriptions();
}
