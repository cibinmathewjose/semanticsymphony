package org.symphonykernel.core;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.symphonykernel.Knowledge;
import org.symphonykernel.KnowledgeDescription;

@Component
public interface IknowledgeBase extends IDelta<KnowledgeDescription> {

    Knowledge GetByName(String name);    

    String GetViewDefByName(String name);

    Map<String, String> getAllKnowledgeDescriptions();
    
    Map<String, String> getActiveKnowledgeDescriptions();

    Map<String, String> getAllVewDescriptions();
}
