/*
 * The MIT License
 *
 * Copyright 2025 cjose.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.symphonykernel.steps;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.transformer.TemplateResolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.mail.internet.MimeMessage;

/**
 * EmailStep sends an email using Spring's JavaMailSender.
 *
 * <p><strong>Knowledge entry configuration</strong></p>
 * <p>The {@code data} field of the Knowledge entry should contain a JSON object
 * with email configuration. All fields support placeholder resolution from
 * resolved values (e.g. {@code {{stepKey}}} or {@code {{input.field}}}).</p>
 * <pre>{@code
 * {
 *   "from": "noreply@example.com",
 *   "to": "user@example.com",
 *   "cc": "manager@example.com",
 *   "bcc": "audit@example.com",
 *   "subject": "Report for {{reportDate}}",
 *   "body": "Hello {{userName}}, please find the report attached.",
 *   "html": true
 * }
 * }</pre>
 *
 * <p>Alternatively, email fields can come from the execution context variables:</p>
 * <ul>
 *   <li>{@code to} – recipient address(es), comma-separated</li>
 *   <li>{@code cc} – CC address(es), comma-separated</li>
 *   <li>{@code bcc} – BCC address(es), comma-separated</li>
 *   <li>{@code subject} – email subject line</li>
 *   <li>{@code body} – email body content</li>
 * </ul>
 *
 * <p>Spring mail properties must be configured:</p>
 * <pre>
 * spring.mail.host=smtp.example.com
 * spring.mail.port=587
 * spring.mail.username=...
 * spring.mail.password=...
 * spring.mail.properties.mail.smtp.auth=true
 * spring.mail.properties.mail.smtp.starttls.enable=true
 * </pre>
 */
@Service("EmailStep")
public class EmailStep extends BaseStep {

    private static final Logger logger = LoggerFactory.getLogger(EmailStep.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private TemplateResolver templateResolver;

    @Autowired
    private Environment environment;

    @Override
    public ChatResponse getResponse(ExecutionContext ctx) {
        ArrayNode jsonArray = objectMapper.createArrayNode();
        Knowledge kb = ctx.getKnowledge();

        try {
            if (mailSender == null) {
                throw new IllegalStateException(
                    "JavaMailSender is not configured. Add spring-boot-starter-mail dependency "
                    + "and configure spring.mail.* properties.");
            }

            JsonNode config = getConfig(kb);
            JsonNode variables = ctx.getVariables();
            Map<String, JsonNode> resolvedValues = ctx.getResolvedValues();

            // Resolve email fields — config takes precedence, variables as fallback
            String from = resolveField(config, variables, resolvedValues, "from",
                    environment.getProperty("spring.mail.username", "noreply@localhost"));
            String to = resolveField(config, variables, resolvedValues, "to", null);
            String cc = resolveField(config, variables, resolvedValues, "cc", null);
            String bcc = resolveField(config, variables, resolvedValues, "bcc", null);
            String subject = resolveField(config, variables, resolvedValues, "subject", "(No Subject)");
            String body = resolveField(config, variables, resolvedValues, "body", "");
            boolean isHtml = config.has("html") && config.get("html").asBoolean(false);

            if (to == null || to.isBlank()) {
                throw new IllegalArgumentException("EmailStep requires a 'to' address");
            }

            logger.info("EmailStep: sending to={}, subject={}", to, subject);

            sendEmail(from, to, cc, bcc, subject, body, isHtml);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("sent", true);
            result.put("to", to);
            result.put("subject", subject);
            jsonArray.add(result);

            saveStepData(ctx, jsonArray);
            logger.info("EmailStep completed successfully");

        } catch (Exception e) {
            logger.error("EmailStep failed: {}", e.getMessage());
            ObjectNode err = objectMapper.createObjectNode();
            err.put("sent", false);
            err.put("errors", e.getMessage());
            jsonArray.add(err);
        }

        ChatResponse response = new ChatResponse();
        response.setData(jsonArray);
        return response;
    }

    private void sendEmail(String from, String to, String cc, String bcc,
                           String subject, String body, boolean isHtml) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(from);
        helper.setTo(parseAddresses(to));
        if (cc != null && !cc.isBlank()) {
            helper.setCc(parseAddresses(cc));
        }
        if (bcc != null && !bcc.isBlank()) {
            helper.setBcc(parseAddresses(bcc));
        }
        helper.setSubject(subject);
        helper.setText(body, isHtml);

        mailSender.send(message);
    }

    private String[] parseAddresses(String addresses) {
        return addresses.split("\\s*,\\s*");
    }

    private String resolveField(JsonNode config, JsonNode variables,
                                Map<String, JsonNode> resolvedValues,
                                String field, String defaultValue) {
        // 1. Try from config (Knowledge data)
        String value = getTextValue(config, field);
        // 2. Fallback to variables (payload)
        if (value == null) {
            value = getTextValue(variables, field);
        }
        // 3. Apply default
        if (value == null) {
            value = defaultValue;
        }
        // 4. Resolve placeholders
        if (value != null && TemplateResolver.hasPlaceholders(value)) {
            value = templateResolver.resolvePlaceholders(value, resolvedValues);
        }
        return value;
    }

    private String getTextValue(JsonNode node, String field) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            String val = node.get(field).asText("");
            return val.isBlank() ? null : val;
        }
        return null;
    }

    private JsonNode getConfig(Knowledge kb) {
        if (kb != null && kb.getData() != null && !kb.getData().isEmpty()) {
            return getParamNode(kb.getData());
        }
        return objectMapper.createObjectNode();
    }
}
