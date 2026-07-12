package com.contractsentinel.drift.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public record SchemaNode(
        String path,
        String type,
        boolean required,
        List<String> enumValues,
        String format,
        String pattern,
        boolean nullable
) {
    public static List<SchemaNode> fromJsonSchema(JsonNode schema, String prefix) {
        List<SchemaNode> nodes = new ArrayList<>();
        if (schema == null) return nodes;

        JsonNode properties = schema.get("properties");
        JsonNode requiredArr = schema.get("required");
        List<String> requiredList = new ArrayList<>();
        if (requiredArr != null && requiredArr.isArray()) {
            requiredArr.forEach(n -> requiredList.add(n.asText()));
        }

        if (properties != null && properties.isObject()) {
            properties.fieldNames().forEachRemaining(field -> {
                JsonNode prop = properties.get(field);
                String path = prefix.isEmpty() ? field : prefix + "." + field;
                boolean isRequired = requiredList.contains(field);
                String type = prop.has("type") ? prop.get("type").asText() : "object";
                List<String> enumVals = new ArrayList<>();
                if (prop.has("enum") && prop.get("enum").isArray()) {
                    prop.get("enum").forEach(e -> enumVals.add(e.asText()));
                }
                String format = prop.has("format") ? prop.get("format").asText() : null;
                String pattern = prop.has("pattern") ? prop.get("pattern").asText() : null;
                boolean nullable = prop.has("nullable") && prop.get("nullable").asBoolean();

                nodes.add(new SchemaNode(path, type, isRequired, enumVals, format, pattern, nullable));

                if ("object".equals(type) || (prop.has("properties") && prop.get("properties").isObject())) {
                    nodes.addAll(fromJsonSchema(prop, path));
                }

                if (prop.has("items") && prop.get("items").isObject()) {
                    JsonNode items = prop.get("items");
                    String itemPath = path + "[]";
                    String itemType = items.has("type") ? items.get("type").asText() : "object";
                    boolean itemRequired = false;
                    List<String> itemEnum = new ArrayList<>();
                    if (items.has("enum") && items.get("enum").isArray()) {
                        items.get("enum").forEach(e -> itemEnum.add(e.asText()));
                    }
                    String itemFormat = items.has("format") ? items.get("format").asText() : null;
                    String itemPattern = items.has("pattern") ? items.get("pattern").asText() : null;
                    boolean itemNullable = items.has("nullable") && items.get("nullable").asBoolean();

                    nodes.add(new SchemaNode(itemPath, itemType, itemRequired, itemEnum, itemFormat, itemPattern, itemNullable));

                    if ("object".equals(itemType) || (items.has("properties") && items.get("properties").isObject())) {
                        nodes.addAll(fromJsonSchema(items, itemPath));
                    }
                }
            });
        }

        return nodes;
    }
}
