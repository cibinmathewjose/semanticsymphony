
package org.symphonykernel.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.KnowledgeDto;
import org.symphonykernel.core.knowledgeBase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class GraphQLStep implements Step {

  
    @Autowired
	PlatformHelper platformHelper;
    @Autowired
    private ObjectMapper objectMapper;

	@Autowired
	knowledgeBase knowledgeBase;

	@Override
	public ArrayNode getResponse(ExecutionContext ctx ) {

        ArrayNode jsonArray = objectMapper.createArrayNode();
        JsonNode variables = ctx.getVariables();
        KnowledgeDto kb =ctx.getKnowledge();
        try {
            if (kb != null && kb.getUrl() != null && !kb.getUrl().isEmpty()) {

                try {
                  
                	 System.out.println("Executing GQL " + kb.getName() +" with "+variables);
                    JsonNode root = executeGraphqlQuery(kb.getUrl(), kb.getData(), variables,ctx.getUserToken());
                    JsonNode res = root.path("data");
                    jsonArray.add(res);
                    System.out.println("Data " + res);
                } catch (Exception e) {
                    ObjectNode err = objectMapper.createObjectNode();
                    err.put("errors", e.getMessage());
                    jsonArray.add(err);
                }

            }

        } catch (Exception e) {
            ObjectNode err = objectMapper.createObjectNode();
            err.put("errors", e.getMessage());
            jsonArray.add(err);
        }

        return jsonArray;
    }
    
    
	@Override
	public JsonNode executeQueryByName(ExecutionContext context){
    	final ArrayNode[] array = new ArrayNode[1];
    	 KnowledgeDto kb = knowledgeBase.GetByName(context.getName());    	 
    		if (kb != null && kb.getUrl() != null && !kb.getUrl().isEmpty()) {
    			
                try {     
                	JsonNode var=context.getVariables();
                	if(context.getConvert())
        			{
                		  JsonTransformer transformer = new JsonTransformer();
                		  var = transformer.compareAndReplaceJson(kb.getParams(), context.getVariables());
                		  context.setVariables(var);
                		  context.setKnowledge(kb);
                		//var=platformHelper.compareAndReplaceJsonv2(kb.getParams(), variables);
        			}
                	array[0]=getResponse(context);
                } catch (Exception e) {
                	// TODO Auto-generated catch block
					e.printStackTrace();
                }

            }    	
    	  return array[0];
    }
    
    @Cacheable(
    	    value = "cSCPCache",
    	    key = "T(org.apache.commons.codec.digest.DigestUtils).sha256Hex(#query) + '_' + #variables"
    	)
    private JsonNode executeGraphqlQuery(String url, String query, JsonNode variables,String token){
         

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // for ms to ms call we used a long lived token in DM and Scanner Service
        // Re: Long-lived token for document scanning
        //headers.add("AuthRoleToken",
        //        "eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiJkb2N1bWVudF91cGxvYWRlciIsImF1ZCI6Imh0dHBzOi8vY29ybmVyc3RvbmUuZWxjLmNvbSIsImV4cCI6MjIxMjcyNTc4MiwiaWF0IjoxNTgyMDA1NzgyLCJqdGkiOiJkb2N1bWVudF91cGxvYWRlciIsIkFwcCI6IlJEUyIsIkFwcFJvbGVzIjpbIlJEU19ET0NTQ0FOIl0sIkFwcEZ1bmN0aW9ucyI6WyJWRlJNU0FVVEgiLCJWRE9DRCIsIlZET0NMQkwiLCJDRE9DRCJdLCJ1c2VyTnVtYmVyIjoxMDM5OCwiand0Q2FjaGVLZXkiOiJFTENMR04tVU5LTk9XTi1FTlYtZG9jdW1lbnRfdXBsb2FkZXIjZDcxMWIzYjUtYWJjOC00MWY4LTgxNjctZjNmYmU1YWFhM2YzIn0.RDWVuHAS9w7LWFiH8Jqn8Jv9LFW_k5MSu6Qk24L_ymmREMYzehewZOJuwLwfd4zKH_gtnLHCte_y1tfTxu9g84mPL3AXr9RqobB6-SnVCiANLfZAaaWG_oF0QORrhWghfMDnp1oIN6ENH7Gijc7LZ2mq7HxntgOgKV8rNDuAggO86BGN2JP8jGMJBObP3CIWigVkgkg422op_i9-minhoPP8To91osoon2VpyjtEQFWTFI5fXwjri6vrfIaYh6UXYRMkGhAzWz_XAvRQzmLWo-mQbc6HxwE6CD5r8qufxsjdm0haS_n6sqAVrBgcX_NzwkNR5o5vDVCZwzb_f3l3tQ");

         //String jwtTknCkey = authStore.getJwtCacheKey();
         headers.add("jwttknckey", token);//"ELCLGN-dev-cjose~RDS");
        // headers.add("LoggerTraceId", MDC.get("LoggerTraceId"));
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
       // body.put("operationName", null);
        body.put("query", query);
        body.put("variables", variables);

        final HttpEntity<String> requestEntity = new HttpEntity<String>(body.toString(), headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, requestEntity, JsonNode.class);

        JsonNode responseBody = response.getBody();        
        return responseBody;
    }
    
    private JsonNode executeGraphqlQueryByResource(String url, Resource resource, JsonNode variables,String token)
			throws Exception {
		InputStream inputStream = resource.getInputStream();
		String query = null;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			query = reader.lines().collect(Collectors.joining("\n"));
		}
		return executeGraphqlQuery(url, query, variables,token);
	}

}