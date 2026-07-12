package com.contractsentinel.ingestion.normalizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class PayloadNormalizer {

    private static final Logger log = LoggerFactory.getLogger(PayloadNormalizer.class);

    private static final Set<String> VOLATILE_FIELD_NAMES = Set.of(
            "timestamp", "ts", "created_at", "updated_at",
            "traceId", "spanId", "requestId", "correlationId"
    );

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);

    private static final String TIMESTAMP_PLACEHOLDER = "__NORMALIZED_TIMESTAMP__";
    private static final String UUID_PLACEHOLDER = "__NORMALIZED_UUID__";

    private final ObjectMapper objectMapper;

    public PayloadNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String normalize(String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode normalized = normalizeNode(root);
            String json = objectMapper.writeValueAsString(normalized);
            return replaceUuids(json);
        } catch (Exception e) {
            log.warn("Failed to normalize payload, returning raw: {}", e.getMessage());
            return replaceUuids(rawPayload);
        }
    }

    private JsonNode normalizeNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode fieldValue = field.getValue();

                if (VOLATILE_FIELD_NAMES.contains(fieldName)) {
                    objectNode.put(fieldName, TIMESTAMP_PLACEHOLDER);
                } else {
                    objectNode.set(fieldName, normalizeNode(fieldValue));
                }
            }
            return objectNode;
        } else if (node.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (JsonNode element : node) {
                arrayNode.add(normalizeNode(element));
            }
            return arrayNode;
        }
        return node.deepCopy();
    }

    private String replaceUuids(String json) {
        return UUID_PATTERN.matcher(json).replaceAll(UUID_PLACEHOLDER);
    }
}
