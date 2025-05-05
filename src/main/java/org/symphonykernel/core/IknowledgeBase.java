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

    String GetViewDefByName(String name);

    Map<String, String> getAllKnowledgeDescriptions();
    
    Map<String, String> getActiveKnowledgeDescriptions();

    Map<String, String> getAllVewDescriptions();
}
