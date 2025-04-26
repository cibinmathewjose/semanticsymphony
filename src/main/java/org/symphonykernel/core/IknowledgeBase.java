package org.symphonykernel.core;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.symphonykernel.Knowledge;

@Component
public interface IknowledgeBase {

    Knowledge GetByName(String name);

    String GetViewDefByName(String name);

    Map<String, String> getAllKnowledgeDescriptions();
    
    Map<String, String> getActiveKnowledgeDescriptions();

    Map<String, String> getAllVewDescriptions();
}
