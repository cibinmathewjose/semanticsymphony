package org.symphonykernel.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.symphonykernel.LLMRequest;
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

    public abstract <R> R execute(LLMRequest request);

    /**
     * Executes the given LLM request, handling prompt splitting and chunking logic
     * when required.
     *
     * @param <R>          the type of the result returned by the LLM function
     * @param request      the request containing system and user prompts, tools,
     *                     and model information
     * @param llmFunction  the function that actually executes the LLM call
     * @return the execution result returned by {@code llmFunction}
     * @throws IllegalArgumentException if both system and user prompts are empty
     */
    protected <R> R processPromptString(LLMRequest request ,Function<LLMRequest, R> llmFunction) {

       
        validatePrompts(request.getSystemMessage(), request.getUserPrompt());

        if (request.getUserPrompt().contains(SPLITTER)) {
            return processAsParts(request.getSystemMessage(), request.getUserPrompt(), request.getTools(), request.getModelName(), llmFunction);
        } else {
            return process(request,llmFunction);
        }
    }
 

    private void validatePrompts(String systemPrompt, String question) throws IllegalArgumentException {
        if ((systemPrompt == null || systemPrompt.trim().isEmpty()) && (question == null || question.trim().isEmpty())) {
            throw new IllegalArgumentException("Please provide a valid question. If multiple prompts in one go, please start with " + HEAD + " and use " + SPLITTER + " to split each section. Use " + FINAL_FORMATTING + " in header to provide final formatting instructions.");
        }
    }

    private <R> R processAsParts(String systemPrompt, String question, Object[] tools, String model, Function<LLMRequest, R> llmFunction) {
        String[] parts = question.split(SPLITTER);
        String header = parts[0];
        var headerPrompt= getBasePrompt( header);
       
        final String basePrompt =headerPrompt!=null? headerPrompt: systemPrompt;
        List<String> partList = new ArrayList<>();
        partList.addAll(List.of(parts).subList(headerPrompt!=null? 1: 0, parts.length));

        // Create a thread pool with a fixed number of threads
        List<CompletableFuture<R>> futures = getFuture(tools, model,  basePrompt, partList, llmFunction);
        String finalFormattingPrompt =  getFormatingPrompt( header);
       
            return getFinalResponse(tools, model, finalFormattingPrompt, futures, llmFunction);
        
    }

    private <R> R getFinalResponse(Object[] tools, String model, String finalFormattingPrompt ,
            List<CompletableFuture<R>> futures, Function<LLMRequest, R> llmFunction) {
        StringBuilder finalResponse = new StringBuilder();
        for (CompletableFuture<R> future : futures) {
            try {
                R response = future.get();
                if (response != null) {
                    finalResponse.append(response).append(System.lineSeparator());
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error processing part in parallel: {}", e.getMessage(), e);
            }
        }
     
        if(finalFormattingPrompt == null || finalFormattingPrompt.isEmpty())
            finalFormattingPrompt=  "Combine the responses into a single coherent answer: " ;
        
        return process(new LLMRequest(finalFormattingPrompt, finalResponse.toString(), tools, model), llmFunction);
        
    }

    private <R> List<CompletableFuture<R>> getFuture(Object[] tools, String model,
            final String basePrompt, List<String> partList, Function<LLMRequest, R> llmFunction) {
        List<CompletableFuture<R>> futures = new ArrayList<>();
        String traceId = MDC.get(Constants.LOGGER_TRACE_ID);
        logger.info("Processing {} prompts in parallel with traceId {}", partList.size(), traceId);
        for (String part : partList) {          
            futures.add(CompletableFuture.supplyAsync(() -> {
                MDC.put(Constants.LOGGER_TRACE_ID, traceId);
                try {
                    return process(new LLMRequest(basePrompt, part, tools, model), llmFunction);
                } catch (Exception e) {
                    logger.error("Error processing part in parallel: {}", e.getMessage(), e);
                    return null;
                } finally {
                    MDC.clear();
                }
            }, executorService));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return futures;
    }

    private String getBasePrompt(String header) {
        String basePrompt = null;
        if (header.contains(HEAD)) {
           // i = 1;
            if (header.contains(FINAL_FORMATTING)) {
                basePrompt = header.substring(header.indexOf(HEAD) + HEAD.length(), header.indexOf(FINAL_FORMATTING)).trim();
            } else {
                basePrompt = header.substring(header.indexOf(HEAD) + HEAD.length()).trim();
            }
        } 
        return basePrompt;
    }
 private String getFormatingPrompt(String header) {
        String finalFormattingPrompt = null;
        if (header.contains(HEAD) && header.contains(FINAL_FORMATTING)) {
             finalFormattingPrompt = header.substring(header.indexOf(FINAL_FORMATTING) + FINAL_FORMATTING.length()).trim();
        } 
        return finalFormattingPrompt;
    }
    private  <R> R process(LLMRequest request,Function<LLMRequest, R> llmFunction) {
        String systemPrompt = request.getSystemMessage();
        String userPrompt = request.getUserPrompt();
        Object[] tools = request.getTools();
        String model = request.getModelName();

        
        int len = systemPrompt.length();
        int len2 = userPrompt.length();

        if (maxInputLength > 4000 && (len + len2 > maxInputLength)) {
            if (systemPrompt.contains(CHUNKS) && len > len2) {
                return executeChunks(systemPrompt, userPrompt, tools, true, model,llmFunction);

            } else if (userPrompt.contains(CHUNKS) && len2 > len) {
                return executeChunks(userPrompt, systemPrompt, tools, false, model,llmFunction);

            } else {
                throw new IllegalArgumentException("Data length execeeded " + maxInputLength + " chars limit"); //128000
            }
        } else if (systemPrompt.contains(CHUNK_PROMPT)) {
            return clearChunkPrompt(systemPrompt, userPrompt, tools, true, model,llmFunction);
        } else if (userPrompt.contains(CHUNK_PROMPT)) {
            return clearChunkPrompt(userPrompt, systemPrompt, tools, false, model,llmFunction);
        } else {
            return llmFunction.apply(request);
        }
    }
    
    private  <R> R clearChunkPrompt(String chunkedPrompt, String prompt, Object[] tools, boolean isSystemPromptChunk, String model, Function<LLMRequest, R> llmFunction) {
        int startIdx = chunkedPrompt.indexOf(CHUNKS);
        String head = chunkedPrompt.substring(0, chunkedPrompt.indexOf(CHUNK_PROMPT));
        String tail = chunkedPrompt.substring(chunkedPrompt.indexOf(CHUNKS) + CHUNKS.length());
        int endIdx = tail.indexOf(CHUNKS);
        tail = tail.substring(endIdx+10);
        startIdx += CHUNKS.length();
        String datapart ;
        if(endIdx>startIdx) {
        	datapart = chunkedPrompt.substring(startIdx + CHUNKS.length(), endIdx);
        	
		}
        else {
        	datapart="NONE";
			logger.warn("No data found between chunk markers in the prompt.");
		}
        if (isSystemPromptChunk) {
            return llmFunction.apply(new LLMRequest(head + System.lineSeparator() + datapart + System.lineSeparator() + tail, prompt, tools, model));
        } else {
            return llmFunction.apply(new LLMRequest(prompt, head + System.lineSeparator() + datapart + System.lineSeparator() + tail, tools, model));
        }
        
    }

    private   <R> R executeChunks(String chunkedPrompt, String prompt, Object[] tools, boolean isSystemPromptChunk, String model,Function<LLMRequest, R> llmFunction) {
        
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
            return null;
        }
        List<CompletableFuture<R>> futures = new ArrayList<>();
        String traceId = MDC.get(Constants.LOGGER_TRACE_ID);
    
        logger.info("Processing {} chunks in parallel with traceId {}", chunks.size(), traceId);
       
        for (String part : chunks) {

            futures.add(CompletableFuture.supplyAsync(() -> {
                MDC.put(Constants.LOGGER_TRACE_ID, traceId);
                try {
                    R result;
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
                    
                    result= llmFunction.apply(new LLMRequest(systemprompt, userprompt, tools, model));
                    logger.info("Processed systemprompt \n{}\n userprompt \n{}\n  Result \n{}", systemprompt, userprompt, result);
                    return result;

                } catch (Exception e) {
                    logger.error("Error processing part in parallel: {}", e.getMessage(), e);
                    return null;
                } finally {
                    MDC.clear();
                }
            }, executorService));

        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        int c = 1;
        StringBuilder finalResponse = new StringBuilder();
        for (CompletableFuture<R> future : futures) {
            try {
                R response = future.get();
                if (response != null) {
                    finalResponse.append("CHUNK ").append(c++).append(System.lineSeparator()).append(response).append(System.lineSeparator());
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error processing part in parallel: {}", e.getMessage(), e);
            }
        }

        return execute(new LLMRequest(chunkPrompt, finalResponse.toString(), tools, model));
    }

}

