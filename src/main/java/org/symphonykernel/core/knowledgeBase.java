package org.symphonykernel.core;

import org.springframework.stereotype.Component;
import org.symphonykernel.KnowledgeDto;

@Component
public interface knowledgeBase {
	KnowledgeDto GetByName(String name);
}
