package com.contractsentinel.drift.classifier;

import com.contractsentinel.drift.model.SchemaNode;
import com.contractsentinel.drift.model.Violation;

import java.util.HashSet;
import java.util.Set;

public class BreakingChangeClassifier {

    public Violation classify(SchemaNode baseline, SchemaNode inferred) {
        String path = baseline.path();

        if (baseline.required() && !inferred.required()) {
            return new Violation(path, Violation.ChangeType.BREAKING,
                    "Required field made optional: " + path, "BREAKING");
        }

        if (!baseline.type().equals(inferred.type())) {
            String changeType = classifyTypeChange(baseline.type(), inferred.type());
            if (changeType != null) {
                return new Violation(path, Violation.ChangeType.valueOf(changeType),
                        "Field type changed from " + baseline.type() + " to " + inferred.type() + ": " + path,
                        changeType);
            }
        }

        if (!baseline.enumValues().isEmpty() || !inferred.enumValues().isEmpty()) {
            Set<String> baseEnums = new HashSet<>(baseline.enumValues());
            Set<String> infEnums = new HashSet<>(inferred.enumValues());
            if (!infEnums.containsAll(baseEnums)) {
                return new Violation(path, Violation.ChangeType.BREAKING,
                        "Enum values removed: " + path, "BREAKING");
            }
            if (infEnums.size() > baseEnums.size()) {
                return new Violation(path, Violation.ChangeType.ADDITIVE,
                        "Enum values added: " + path, "ADDITIVE");
            }
        }

        if (baseline.format() != null && inferred.format() != null
                && !baseline.format().equals(inferred.format())) {
            return new Violation(path, Violation.ChangeType.BREAKING,
                    "Format changed from " + baseline.format() + " to " + inferred.format() + ": " + path,
                    "BREAKING");
        }

        if (baseline.pattern() != null && inferred.pattern() != null
                && !baseline.pattern().equals(inferred.pattern())) {
            return new Violation(path, Violation.ChangeType.BREAKING,
                    "Pattern changed: " + path, "BREAKING");
        }

        if (!baseline.nullable() && inferred.nullable()) {
            return new Violation(path, Violation.ChangeType.WARNING,
                    "Response schema now nullable: " + path, "WARNING");
        }

        return null;
    }

    private String classifyTypeChange(String baseType, String inferredType) {
        if ("number".equals(baseType) && "integer".equals(inferredType)) {
            return "BREAKING";
        }
        if ("integer".equals(baseType) && "number".equals(inferredType)) {
            return "ADDITIVE";
        }
        if ("string".equals(baseType) && ("number".equals(inferredType) || "integer".equals(inferredType))) {
            return "BREAKING";
        }
        if (("number".equals(baseType) || "integer".equals(baseType)) && "string".equals(inferredType)) {
            return "BREAKING";
        }
        if ("boolean".equals(baseType) && !"boolean".equals(inferredType)) {
            return "BREAKING";
        }
        if (!baseType.equals(inferredType)) {
            return "BREAKING";
        }
        return null;
    }
}
