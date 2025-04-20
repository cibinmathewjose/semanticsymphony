package org.symphonykernel.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.symphonykernel.core.JsonTransformer;

import static org.assertj.core.api.Assertions.assertThat;

class JsonProcessorTest {

    private ObjectMapper mapper;
    private JsonTransformer processor;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        processor = new JsonTransformer();
    }

   
    @Test
    void test2() throws Exception {
        /*String templateJson = "{\r\n"
        		+ "  \"input\": {\r\n"
        		+ "    \"compareType\": \"CMP\",\r\n"
        		+ "    \"controlMethod\": \"SIMILARITY\",\r\n"
        		+ "    \"formulaIds\": [\r\n"
        		+ "      {\"type\":\"number\"}\r\n"
        		+ "    ],\r\n"
        		+ "    \"useLabelPercent\": \"Y\",\r\n"
        		+ "    \"ingredientDescription\": \"TRADE\"\r\n"
        		+ "  }\r\n"
        		+ "}";
        String payloadJson= "[{\"formula_id\": 1},{\"formula_id\": 2}]";
        JsonNode result = processor.processJson(null,mapper.readTree(templateJson),mapper.readTree(payloadJson),false);

        assertThat(result.at("/input/formulaIds/").get(0)).isEqualTo(1);*/
    }
    /*
    @Test
    void test1() throws Exception {
        String templateJson = "{\"formula_number\":{\"type\":\"string\"}}";
        String payloadJson= "{\"formula_number\": \"VC6413/1\",  \"country\": \"china\"}";
        JsonNode result = processor.processJson(null,mapper.readTree(templateJson),mapper.readTree(payloadJson),false);

        assertThat(result.at("/formula_number").asText()).isEqualTo("VC6413/1");
    }
    @Test
    void test3() throws Exception {
        String templateJson = "[\"formula_number\":{\"type\":\"string\"}]";
        String payloadJson= " [{\"formula_number\": \"VC6413/1\"},{\"formula_number\": \"VC6413/2\"}]";
        JsonNode result = processor.processJson(null,mapper.readTree(templateJson),mapper.readTree(payloadJson),true);

        assertThat(result.get(0).at("/formula_number").asText()).isEqualTo("VC6413/1");
    }
    @Test
    void testEmptyJson() throws Exception {      
        String templateJson = "{}";
        String payloadJson= "{}";
        JsonNode result = processor.processJson(null,mapper.readTree(templateJson),mapper.readTree(payloadJson),false);

        assertThat(result.isObject()).isTrue();
        assertThat(result.size()).isZero();
    }*/
}
