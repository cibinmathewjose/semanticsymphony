package org.symphonykernel.core;

import java.lang.reflect.InvocationTargetException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatRequest;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.QueryType;
import org.symphonykernel.UserSession;
import org.symphonykernel.starter.AzureOpenAIConnectionProperties;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion;
import com.microsoft.semantickernel.implementation.CollectionUtil;
import com.microsoft.semantickernel.orchestration.InvocationContext;
import com.microsoft.semantickernel.orchestration.ToolCallBehavior;
import com.microsoft.semantickernel.plugin.KernelPlugin;
import com.microsoft.semantickernel.plugin.KernelPluginFactory;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;
import com.microsoft.semantickernel.services.chatcompletion.ChatMessageContent;

@Service
public class Agent implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

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
    public static Object createObject(String fullyQualifiedName) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        // Load the class using the class loader.
        Class<?> clazz = Class.forName(fullyQualifiedName);

        //Use Spring to create the instance, which will handle autowiring
        Object instance = applicationContext.getAutowireCapableBeanFactory().createBean(clazz);
        return instance;
    }

    @Autowired
    IUserSessionBase userSessionsBase;

    @Autowired
    IknowledgeBase knowledgeBaserepo;

    @Autowired
    AzureOpenAIHelper openAI;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    Symphony symphony;

    @Autowired
    GraphQLStep graphQLHelper;

    @Autowired
    SqlStep sqlAssistant;

    @Autowired
    PlatformHelper platformHelper;

    private static final String MODEL_ID = System.getenv()
            .getOrDefault("MODEL_ID", "gpt-4o");

    ChatCompletionService chat;

    public Knowledge matchKnowledge(String question) {
        Map<String, String> knowldgeDesc = knowledgeBaserepo.getAllKnowledgeDescriptions();
        String jsonString;
        try {
            jsonString = objectMapper.writeValueAsString(knowldgeDesc);
            String prompt = "Analyze the users question " + question + " and identify the exact matched key value pair from " + jsonString + " and respond with the accurate match "
                    + "the users intention expressed in the question should exactly match with the value Field "
                    + "only just include the exact key name of the macthed value and nothing else in the response and do not explain"
                    + "if could not find a accurate macth just say 'NONE'";

            String k = openAI.getLastMessageSynchronous(prompt);

            if (k != null && !"NONE".equals(k)) {
                return knowledgeBaserepo.GetByName(k.trim());
            }

        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;

    }

    public String ParamParser(String paramJSON, String question) {
        String prompt = "Analyze the json " + paramJSON + " and users question " + question + " and create a base64 string by mapping the values from users question to the correct field name strictly following the field names in the provided json. "
                + "Only just include the exact base64 text in the response and do not explain"
                + "\r\n"
                + "Example 1"
                + "Input:  {\"frm_id\": {\"type\":\"number\" }} \r\n"
                + "Question :  Show the details of formula 1111\r\n"
                + "Output : eyJmcm1faWQiOiAxMTExfQ=="
                + "\r\n"
                + "Example 2\r\n"
                + "Input:   {\"formula_number\":{\"type\":\"string\"},\"country\":{\"type\":\"string\"}}\r\n"
                + "Question :  review formula 1111 for china\r\n"
                + "Output : eyJmb3JtdWxhX251bWJlciI6IDExMTEsImNvdW50cnkiOiJjaGluYSJ9"
                + "\r\n"
                + "Example 3\r\n"
                + "Input:[{\"formula_number\":{\"type\":\"string\"}}]\r\n"
                + "Question: Compare formulas TRE1665/1 with TRE1665/2\r\n"
                + "Output: W3siZm9ybXVsYV9udW1iZXIiOiAiVFJFMTY2NS8xIn0seyJmb3JtdWxhX251bWJlciI6IlRSRTE2NjUvMiJ9XQ=="
                + "\r\n"
                + "Example 4\r\n"
                + "Input: {\"formulaId\":[{\"type\":\"number\"}]}\r\n"
                + "Question: Compare formulas 1,2\r\n"
                + "Output: eyJmb3JtdWxhSWQiOlsxLDJdfQ==\r\n"
                + "\r\n"
                + "Example 5\r\n"
                + "Input:[{\"formula_number\":{\"type\":\"string\"}}]\r\n"
                + "Question: compare formula TR7401035 to TRT003B02\r\n"
                + "Output: W3siZm9ybXVsYV9udW1iZXIiOiAiVFI3NDAxMDM1ICJ9LHsiZm9ybXVsYV9udW1iZXIiOiJUUlQwMDNCMDIifV0=";
        String k = openAI.getLastMessageSynchronous(prompt);
        if (k != null && !"NONE".equals(k)) {
            return k.trim();
        }
        return null;
    }

    public String matchSelectQuery(String question) {
        Map<String, String> knowldgeDesc = knowledgeBaserepo.getAllVewDescriptions();
        String jsonString;
        try {
            jsonString = objectMapper.writeValueAsString(knowldgeDesc);

            String prompt = "Analyze the users question " + question + " and identify the exact matched JSON Object from " + jsonString + " and respond with the accurate match "
                    + "Parse the response as JSON "
                    + "only include DATA field value "
                    + "do not include ```json in the response";

            String k = openAI.getLastMessageSynchronous(prompt);
            if (k != null && !"NONE".equals(k)) {
                String vDef = knowledgeBaserepo.GetViewDefByName(k.trim());
                if (vDef != null) {
                    return getQuery(question, vDef);
                }
            }

        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    private String getQuery(String question, String json) {
        String prompt = "You are a program to generate SQL Queries which are ready to be executed. "
                + "The text you generated should only contain single SQL select statement starting with \"select\" "
                + "always use friendly column name for example use record count instead of COUNT(*) "
                + "do not include ```sql "
                + "for string comparisons always use uppercase on both sides "
                + "do not add ; at the end "
                + "always include the schema name "//\"CJOSE\" where ever schema name is not defined "
                + "Only include the requested columns in the select statement "
                + "do not use space in column aliases "
                + "Make sure the select statement  column names and view names are correct as per the model "
                + "strictly follow the request " + question
                + "Please ensure the query is strictly based on the following table definitions as Reference Data Model "
                + " Data Model";
        String k = openAI.getLastMessageSynchronous(prompt);
        if (k != null && !"NONE".equals(k)) {
            return k;
        }
        return null;
    }

    public Agent(AzureOpenAIConnectionProperties connectionProperties) {
        OpenAIAsyncClient client;
        client = new OpenAIClientBuilder()
                .credential(new AzureKeyCredential(connectionProperties.getKey()))
                .endpoint(connectionProperties.getEndpoint())
                .buildAsyncClient();

        //String pluginDirectory2 = "./plugins";
        // String pluginName2 = "PluginTwo";
        // KernelPlugin plugin2 = KernelPluginFactory.importPluginFromDirectory(pluginDirectory2, pluginName2, null);

        /*
    String yaml = EmbeddedResourceLoader.readFile("petstore.yaml", ExamplePetstoreImporter.class);

    KernelPlugin plugin = SemanticKernelOpenAPIImporter
       .builder()
       .withPluginName("petstore")
       .withSchema(yaml)
       .withServer("http://localhost:8090/api/v3")
       .build();

         */
        chat = OpenAIChatCompletion.builder()
                .withModelId(MODEL_ID)
                .withOpenAIAsyncClient(client)
                .build();

    }

    public ChatResponse process(ChatRequest request) {
        UserSession info = new UserSession();
        info.setSessionID(request.getSession());
        info.setUserId(request.getUser());
        info.setRequestId(UUID.randomUUID().toString());
        info.setUserInput(request.getQuery());
        info.setCreateDt(Calendar.getInstance().getTime());
        info.setStatus("RECEIVED");
        info = userSessionsBase.save(info);
        ChatResponse a = new ChatResponse();
        a.setRequestId(info.getRequestId());

        List<UserSession> sessions = userSessionsBase.getSession(request.getSession());
        ChatHistory chatHistory = new ChatHistory();
        if (sessions != null && !sessions.isEmpty()) {
            for (UserSession session : sessions) {
                if (session.getUserInput() != null && session.getBotResponse() != null) {
                    chatHistory.addUserMessage(session.getUserInput());
                    chatHistory.addSystemMessage(session.getBotResponse());
                }
            }
        }
        chatHistory.addUserMessage(request.getQuery());

        Knowledge knowledge = matchKnowledge(request.getQuery());
        if (knowledge == null) {
            String query = matchSelectQuery(request.getQuery());
            if (query != null) {
                a.setData(sqlAssistant.executeSqlQuery(query));
                a.setStatusCode("SUCCESS");
            }
        } else {
            if (knowledge.getParams() != null && (request.getPayload() == null || "NONE".equals(request.getPayload()))) {
                String params = ParamParser(knowledge.getParams(), request.getQuery());
                request.setPayload(params);
            }
            ExecutionContext ctx = new ExecutionContext();
            ctx.setKnowledge(knowledge);
            ctx.setUsersQuery(request.getQuery());
            ctx.setVariables(request.getVariables());
            ctx.setHttpHeaderProvider(request.getHeaderProvider());
            // if (variables != null&&variables.isEmpty()&&variables.isNull()) {
            if (knowledge.getType() == QueryType.SQL) {
                a.setData(sqlAssistant.getResponse(ctx));
                a.setStatusCode("SUCCESS");
            } else if (knowledge.getType() == QueryType.GRAPHQL) {
                a.setData(graphQLHelper.getResponse(ctx));
                a.setStatusCode("SUCCESS");
            } else if (knowledge.getType() == QueryType.SYMPHNOY) {
                a.setData(symphony.getResponse(ctx));
                a.setStatusCode("SUCCESS");
            } else if (knowledge.getType() == QueryType.PLUGIN) {
                String msg = chat(knowledge.getData(), chatHistory);
                a.setMessage(msg);
                a.setStatusCode("PROMPT");
            }
            // }
            if (knowledge.getCard() != null) {
                a.setMessage(platformHelper.generateAdaptiveCardJson(a.getData().get(0), knowledge.getCard()));

            }
        }
        info.setBotResponse(a.getMessage());
        info.setData(a.getData() != null ? a.getData().toPrettyString() : "");

        info.setModifyDt(Calendar.getInstance().getTime());
        info.setStatus(a.getStatusCode());
        userSessionsBase.save(info);

        return a;
    }

    public String chat(String pluginName, ChatHistory chatHistory) {

        Kernel kernel = loadPlugin(pluginName);
        if (kernel != null) {
            InvocationContext invocationContext = InvocationContext.builder()
                    .withToolCallBehavior(ToolCallBehavior.allowAllKernelFunctions(true))
                    .build();

            List<ChatMessageContent<?>> messages = chat
                    .getChatMessageContentsAsync(chatHistory, kernel, invocationContext)
                    .block();

            ChatMessageContent<?> result = CollectionUtil.getLastOrNull(messages);

            return result.getContent();
        } else {
            return null;
        }
    }

    private Kernel loadPlugin(String pluginName) {
        KernelPlugin plugin;
        try {
            plugin = KernelPluginFactory.createFromObject(createObject(pluginName), pluginName);

            Kernel kernel = Kernel.builder()
                    .withPlugin(plugin)
                    .withAIService(ChatCompletionService.class, chat)
                    .build();
            return kernel;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException
                | NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

}
