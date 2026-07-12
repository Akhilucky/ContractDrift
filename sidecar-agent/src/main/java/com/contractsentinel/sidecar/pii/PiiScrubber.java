package com.contractsentinel.sidecar.pii;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class PiiScrubber {

    private static final Logger log = LoggerFactory.getLogger(PiiScrubber.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", Pattern.CASE_INSENSITIVE);

    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b");

    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b(?!000|666|9\\d{2})\\d{3}-(?!00)\\d{2}-(?!0000)\\d{4}\\b");

    private static final Pattern JWT_PATTERN = Pattern.compile(
            "eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+");

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b\\+?1?[-.\\s]?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b");

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "\\b(?:sk-[a-zA-Z0-9]{20,}|api[-_]?key[-_]?[=:][\\s]*[a-zA-Z0-9]{16,}|[a-zA-Z0-9]{32,64})\\b");

    private static final List<PiiPattern> PATTERNS = List.of(
            new PiiPattern(EMAIL_PATTERN, "[REDACTED_EMAIL]"),
            new PiiPattern(CREDIT_CARD_PATTERN, "[REDACTED_CREDIT_CARD]"),
            new PiiPattern(SSN_PATTERN, "[REDACTED_SSN]"),
            new PiiPattern(JWT_PATTERN, "[REDACTED_JWT]"),
            new PiiPattern(PHONE_PATTERN, "[REDACTED_PHONE]"),
            new PiiPattern(UUID_PATTERN, "[REDACTED_UUID]"),
            new PiiPattern(API_KEY_PATTERN, "[REDACTED_API_KEY]")
    );

    public String scrub(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return rawContent;
        }

        try {
            JsonNode root = MAPPER.readTree(rawContent);
            scrubNode(root);
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            log.debug("Content is not valid JSON, applying regex scrubbing");
            return applyRegexPatterns(rawContent);
        }
    }

    private void scrubNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fieldNames().forEachRemaining(field -> {
                JsonNode child = objectNode.get(field);
                if (child instanceof TextNode) {
                    String scrubbed = applyRegexPatterns(child.asText());
                    objectNode.put(field, scrubbed);
                } else {
                    scrubNode(child);
                }
            });
        } else if (node instanceof ArrayNode arrayNode) {
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode child = arrayNode.get(i);
                if (child instanceof TextNode) {
                    String scrubbed = applyRegexPatterns(child.asText());
                    arrayNode.set(i, new TextNode(scrubbed));
                } else {
                    scrubNode(child);
                }
            }
        }
    }

    private String applyRegexPatterns(String input) {
        if (input == null) {
            return null;
        }
        String result = input;
        for (PiiPattern pattern : PATTERNS) {
            result = pattern.pattern().matcher(result).replaceAll(pattern.replacement());
        }
        return result;
    }

    private record PiiPattern(Pattern pattern, String replacement) {}
}
