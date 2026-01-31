package org.symphonykernel.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.symphonykernel.config.AzureOpenAIConnectionProperties;
import org.symphonykernel.config.Constants;
import org.symphonykernel.transformer.JsonTransformer;


public abstract class AIClientBase {

    private static final Logger logger = LoggerFactory.getLogger(AIClientBase.class);
    int maxInputLength = 100000; // Default max input length
    private static final String SPLITTER = "<!SplitPromptHere!>";
    private static final String HEAD = "<!PromptHead!>";
    private static final String FINAL_FORMATTING = "<!FinalResultFormat!>";
    private static final String CHUNKS = "<!Chunks!>";
    private static final String CHUNK_PROMPT = "<!ChunksPrompt!>";
    
   
   JsonTransformer jsonTransformer;
   protected AzureOpenAIConnectionProperties conProperties;

    private static int MAX_PARALLEL_EXECUTIONS = 5; // Configurable limit for parallel executions
    private final ExecutorService executorService;

    protected AIClientBase(AzureOpenAIConnectionProperties connectionProperties) {
        conProperties=connectionProperties;

        if (connectionProperties.getMaxInputLength() > 0) {
            maxInputLength = connectionProperties.getMaxInputLength();
        }

        if (connectionProperties.getMaxParallel() > 0) {
            MAX_PARALLEL_EXECUTIONS = connectionProperties.getMaxParallel();
        }

        executorService = Executors.newFixedThreadPool(MAX_PARALLEL_EXECUTIONS);
        jsonTransformer = new JsonTransformer();
    }

    public abstract String execute(String systemPrompt, String userPrompt, Object[] tools, String model);

    /**
     * Executes a system prompt and user prompt.
     *
     * @param systemPrompt the system prompt to provide context for the
     * assistant
     * @param question the user prompt containing the question or task
     * @return the execution result as a string
     */
    protected String processPromptString(String systemPrompt, String question, Object[] tools, String model) {

        if ((systemPrompt == null || systemPrompt.trim().isEmpty()) && (question == null || question.trim().isEmpty())) {
            return "Please provide a valid question. If multiple prompts in one go, please start with " + HEAD + " and use " + SPLITTER + " to split each section. Use " + FINAL_FORMATTING + " in header to provide final formatting instructions.";
        }

        if (question.contains(SPLITTER)) {
            String[] parts = question.split(SPLITTER);
            StringBuilder finalResponse = new StringBuilder();
            int i = 0;
            final String basePrompt;
            String finalFormattingPrompt = "";

            if (parts[0].contains(HEAD)) {
                i = 1;
                String header = parts[0];
                if (parts[0].contains(FINAL_FORMATTING)) {
                    basePrompt = header.substring(header.indexOf(HEAD) + HEAD.length(), header.indexOf(FINAL_FORMATTING)).trim();
                    finalFormattingPrompt = header.substring(header.indexOf(FINAL_FORMATTING) + FINAL_FORMATTING.length()).trim();
                } else {
                    basePrompt = parts[0].substring(header.indexOf(HEAD) + HEAD.length()).trim();
                }
            } else {
                basePrompt = systemPrompt;
            }

            // Create a thread pool with a fixed number of threads
            List<CompletableFuture<String>> futures = new ArrayList<>();
            String traceId = MDC.get(Constants.LOGGER_TRACE_ID);
            logger.info("Processing {} prompts in parallel with traceId {}", parts.length, traceId);

            for (; i < parts.length; i++) {
                String part = parts[i];
                futures.add(CompletableFuture.supplyAsync(() -> {
                    MDC.put(Constants.LOGGER_TRACE_ID, traceId);
                    try {
                        return process(basePrompt, part, tools, model);
                    } catch (Exception e) {
                        logger.error("Error processing part in parallel: {}", e.getMessage(), e);
                        return "Error processing part: " + e.getMessage();
                    } finally {
                        MDC.clear();
                    }
                }, executorService));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            for (CompletableFuture<String> future : futures) {
                try {
                    String response = future.get();
                    if (response != null) {
                        finalResponse.append(response).append(System.lineSeparator());
                    }
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Error processing part in parallel: {}", e.getMessage(), e);
                }
            }

            if (!finalFormattingPrompt.isEmpty()) {
                return process(finalFormattingPrompt, finalResponse.toString(), tools, model);
            }
            return finalResponse.toString();
        } else {
            return process(systemPrompt, question, tools, model);
        }
    }

    private String process(String systemPrompt, String userPrompt, Object[] tools, String model) {
        if (systemPrompt == null || StringUtils.isEmpty(systemPrompt) || systemPrompt.equals(userPrompt)) {
            systemPrompt = "You are an AI Aissistant that helps people find information from the provided context data.";
        } else {
            logger.info("Processing data with LLM <!PromptHead!>\r\n {} \r\n  <!SplitPromptHere!> {}\r\n", systemPrompt, userPrompt);

        }
        int len = systemPrompt.length();
        int len2 = userPrompt.length();

        if (maxInputLength > 4000 && (len + len2 > maxInputLength)) {
            if (systemPrompt.contains(CHUNKS) && len > len2) {
                return executeChunks(systemPrompt, userPrompt, tools, true, model);

            } else if (userPrompt.contains(CHUNKS) && len2 > len) {
                return executeChunks(userPrompt, systemPrompt, tools, false, model);

            } else {
                return "Data length execeeded " + maxInputLength + " chars limit"; //128000
            }
        } else if (systemPrompt.contains(CHUNK_PROMPT)) {
            return clearChunkPrompt(systemPrompt, userPrompt, tools, true, model);
        } else if (userPrompt.contains(CHUNK_PROMPT)) {
            return clearChunkPrompt(userPrompt, systemPrompt, tools, false, model);
        } else {
            return execute(systemPrompt, userPrompt, tools, model);
        }
    }

    private String clearChunkPrompt(String chunkedPrompt, String prompt, Object[] tools, boolean isSystemPromptChunk, String model) {
        int startIdx = chunkedPrompt.indexOf(CHUNKS);
        String head = chunkedPrompt.substring(0, chunkedPrompt.indexOf(CHUNK_PROMPT));
        String tail = chunkedPrompt.substring(chunkedPrompt.indexOf(CHUNKS) + CHUNKS.length());
        int endIdx = tail.indexOf(CHUNKS);
        tail = tail.substring(endIdx+10);
        String datapart = chunkedPrompt.substring(startIdx + CHUNKS.length(), endIdx);
        if (isSystemPromptChunk) {
            return process(head + System.lineSeparator() + datapart + System.lineSeparator() + tail, prompt, tools, model);
        } else {
            return process(prompt, head + System.lineSeparator() + datapart + System.lineSeparator() + tail, tools, model);
        }
    }

    private String executeChunks(String chunkedPrompt, String prompt, Object[] tools, boolean isSystemPromptChunk, String model) {
        
        int startIdx = chunkedPrompt.indexOf(CHUNKS);
        String chunkPrompt;
        String tempHead;
        String head;
        if (chunkedPrompt.contains(CHUNK_PROMPT)) {
            tempHead = chunkedPrompt.substring(0, chunkedPrompt.indexOf(CHUNK_PROMPT));
            chunkPrompt = chunkedPrompt.substring(chunkedPrompt.indexOf(CHUNK_PROMPT)+CHUNK_PROMPT.length(), startIdx);
        } else {
            tempHead = chunkedPrompt.substring(0, startIdx);
            chunkPrompt = "You are provided with a part of the data in each chunk. All chunks follow the same structure and format.Combine data across all chunks until all chunks have been processed and provide a consolidated response based on the combined data from all chunks keeping the original structure and format. Do not make up any data.";
        }

        String remainder = chunkedPrompt.substring(chunkedPrompt.indexOf(CHUNKS) + CHUNKS.length());
        int endIdx = remainder.indexOf(CHUNKS);
        String tail = remainder.substring(endIdx+CHUNKS.length());
        String datapart = remainder.substring(0, endIdx).trim();

        List<String> chunks;
        try {
            if(datapart.startsWith(JsonTransformer.JSON) )
            {
                 chunks = jsonTransformer.chunkJsonArray(datapart.substring(JsonTransformer.JSON.length()), maxInputLength - tempHead.length() - tail.length() - prompt.length());                
                 head=tempHead;
            }
            else if(datapart.startsWith(JsonTransformer.LLM_OPTIMIZED_DATA)) {
                chunks = jsonTransformer.chunkCompressedJsonArray(datapart.substring(JsonTransformer.LLM_OPTIMIZED_DATA.length()), maxInputLength - tempHead.length() - tail.length() - prompt.length());                
                if(chunks!=null && !chunks.isEmpty()&&chunks.size()>1) {
                 head= tempHead + "\n" +chunks.get(0);
                 chunks.remove(0);
                }               
                else
                 head=tempHead;
            }
            else
            {
                chunks = jsonTransformer.chunkJsonArray(datapart, maxInputLength - tempHead.length() - tail.length() - prompt.length());
                head=tempHead;
            }
           
        } catch (Exception e) {
            logger.error("Error processing chunks {}", e.getMessage(), e);
            return "Error processing chunk " + e.getMessage();
        }
        List<CompletableFuture<String>> futures = new ArrayList<>();
        String traceId = MDC.get(Constants.LOGGER_TRACE_ID);
    
        logger.info("Processing {} chunks in parallel with traceId {}", chunks.size(), traceId);
       
        for (String part : chunks) {

            futures.add(CompletableFuture.supplyAsync(() -> {
                MDC.put(Constants.LOGGER_TRACE_ID, traceId);
                try {
                    String result;
                    logger.info("Processing chunks on {}", isSystemPromptChunk ? "system prompt" : "user prompt");
                    String systemprompt;
                    String userprompt;
                    if (isSystemPromptChunk) {   
                        systemprompt= head + System.lineSeparator() + part + System.lineSeparator() + tail;
                        userprompt= prompt;                     
                    } else {
                        systemprompt= prompt;
                        userprompt= head + System.lineSeparator() + part + System.lineSeparator() + tail;
                    }
                    
                    result= execute(systemprompt, userprompt, tools, model);
                    logger.info("Processed systemprompt \n{}\n userprompt \n{}\n  Result \n{}", systemprompt, userprompt, result);
                    return result;

                } catch (Exception e) {
                    logger.error("Error processing part in parallel: {}", e.getMessage(), e);
                    return "Error processing part: " + e.getMessage();
                } finally {
                    MDC.clear();
                }
            }, executorService));

        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        int c = 1;
        StringBuilder finalResponse = new StringBuilder();
        for (CompletableFuture<String> future : futures) {
            try {
                String response = future.get();
                if (response != null) {
                    finalResponse.append("CHUNK ").append(c++).append(System.lineSeparator()).append(response).append(System.lineSeparator());
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error processing part in parallel: {}", e.getMessage(), e);
            }
        }

        return execute(chunkPrompt, finalResponse.toString(), tools, model);
    }

}
