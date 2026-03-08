package org.symphonykernel.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.symphonykernel.Knowledge;
import org.symphonykernel.core.IStep;
import org.symphonykernel.steps.AgenticStep;
import org.symphonykernel.steps.AuthenticationStep;
import org.symphonykernel.steps.DatabaseStep;
import org.symphonykernel.steps.DocumentStep;
import org.symphonykernel.steps.EmailStep;
import org.symphonykernel.steps.FileStep;
import org.symphonykernel.steps.GraphQLStep;
import org.symphonykernel.steps.HumanInLoopStep;
import org.symphonykernel.steps.PluginStep;
import org.symphonykernel.steps.RESTStep;
import org.symphonykernel.steps.SqlStep;
import org.symphonykernel.steps.Symphony;
import org.symphonykernel.steps.ToolStep;
import org.symphonykernel.steps.VelocityStep;
import org.symphonykernel.steps.WebSearchStep;

/**
 * Factory for retrieving the appropriate IStep executor based on the Knowledge type.
 * <p>
 * This class uses Spring dependency injection to provide various step implementations
 * and returns the correct one for a given Knowledge instance.
 * </p>
 */
@Component
public class KnowledgeExecuterFactory {
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeExecuterFactory.class);

    @Autowired
    SqlStep sqlAssistant;

    @Autowired
    @Qualifier("GraphQLStep")
    GraphQLStep graphQLHelper;

    @Autowired
    PluginStep pluginStep;

    @Autowired
    ToolStep toolStep;

    @Autowired
    VelocityStep velocityTemplateEngine;

    @Autowired
    @Qualifier("RESTStep")
    RESTStep restHelper;

    @Autowired
    FileStep fileUrlHelper;

    @Autowired
    DocumentStep documentStep;

    @Autowired
    DatabaseStep databaseStep;

    @Autowired
    @Qualifier("AuthenticationStep")
    AuthenticationStep authenticationStep;

    @Autowired
    @Qualifier("WebSearchStep")
    WebSearchStep webSearchStep;

    @Autowired
    @Qualifier("EmailStep")
    EmailStep emailStep;

    @Autowired
    @Qualifier("HumanInLoopStep")
    HumanInLoopStep humanInLoopStep;

    
    Symphony symphony;    
    AgenticStep agenticStep;

    /**
     * Returns the appropriate IStep executor for the given Knowledge type.
     *
     * @param knowledge the Knowledge instance containing the query type
     * @return the IStep executor for the specified knowledge type, or null if not found
     */
    public IStep getExecuter(Knowledge knowledge) {

        if (knowledge == null || knowledge.getType() == null) {
            logger.warn("Knowledge or its type is null");
            return null;
        }
        logger.info("getting executter for " + knowledge.getType());
        switch (knowledge.getType()) {
            case SQL -> {
                return sqlAssistant;
            }
            case GRAPHQL -> {
                return graphQLHelper;
            }
            case SYMPHONY -> {
                if(symphony == null){
                    symphony = (Symphony) initClass(Symphony.class);
                }
                return symphony;
            }
            case PLUGIN -> {
                return pluginStep;
            }
            case TOOL -> {
                return toolStep;
            }
            case VELOCITY -> {
                return velocityTemplateEngine;
            }
            case REST -> {
                return restHelper;
            }
            case FILE -> {
                return fileUrlHelper;
            }
            case SHAREPOINT -> {
                throw new UnsupportedOperationException("SHAREPOINT QueryType is not implemented");
            }
           case AGENTIC -> {
               if(agenticStep == null){
                   agenticStep = (AgenticStep) initClass(AgenticStep.class);
               }
               return agenticStep;
            }
            case DOCUMENT -> {
                return documentStep;
            }
            case DATABASE -> {
                return databaseStep;
            }
            case AUTH -> {
                return authenticationStep;
            }
            case WEBSEARCH -> {
                return webSearchStep;
            }
            case EMAIL -> {
                return emailStep;
            }
            case HUMANLOOP -> {
                return humanInLoopStep;
            }
            default -> {
                logger.warn("Unhandled QueryType: " + knowledge.getType());
                return null;
            }
        }
    }

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    private <T> T initClass(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }
}
