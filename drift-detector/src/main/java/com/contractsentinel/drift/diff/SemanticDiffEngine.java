package com.contractsentinel.drift.diff;

import com.contractsentinel.drift.classifier.BreakingChangeClassifier;
import com.contractsentinel.drift.model.SchemaNode;
import com.contractsentinel.drift.model.Violation;
import com.contractsentinel.drift.model.Violation.ChangeType;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record DriftResult(List<Violation> violations, int driftScore) {
}

public class SemanticDiffEngine {

    private final BreakingChangeClassifier classifier = new BreakingChangeClassifier();

    public DriftResult computeDrift(JsonNode baselineSchema, JsonNode inferredSchema) {
        List<SchemaNode> baselineNodes = SchemaNode.fromJsonSchema(baselineSchema, "");
        List<SchemaNode> inferredNodes = SchemaNode.fromJsonSchema(inferredSchema, "");

        List<Violation> violations = new ArrayList<>();

        Set<String> baselinePaths = new HashSet<>();
        for (SchemaNode n : baselineNodes) baselinePaths.add(n.path());

        Set<String> inferredPaths = new HashSet<>();
        for (SchemaNode n : inferredNodes) inferredPaths.add(n.path());

        for (SchemaNode baseline : baselineNodes) {
            String path = baseline.path();
            if (!inferredPaths.contains(path)) {
                ChangeType type = baseline.required()
                        ? ChangeType.BREAKING
                        : ChangeType.WARNING;
                violations.add(new Violation(
                        path,
                        type,
                        (type == ChangeType.BREAKING ? "Required field removed" : "Optional field removed") + ": " + path,
                        type.name()
                ));
            } else {
                SchemaNode inferred = inferredNodes.stream()
                        .filter(n -> n.path().equals(path))
                        .findFirst().orElse(null);
                if (inferred != null) {
                    Violation v = classifier.classify(baseline, inferred);
                    if (v != null) violations.add(v);
                }
            }
        }

        for (SchemaNode inferred : inferredNodes) {
            String path = inferred.path();
            if (!baselinePaths.contains(path)) {
                ChangeType type = inferred.required()
                        ? ChangeType.BREAKING
                        : ChangeType.ADDITIVE;
                violations.add(new Violation(
                        path,
                        type,
                        (type == ChangeType.BREAKING ? "New required field added: " : "New optional field added: ") + path,
                        type.name()
                ));
            }
        }

        int breaking = (int) violations.stream().filter(v -> v.changeType() == ChangeType.BREAKING).count();
        int warning = (int) violations.stream().filter(v -> v.changeType() == ChangeType.WARNING).count();
        int additive = (int) violations.stream().filter(v -> v.changeType() == ChangeType.ADDITIVE).count();
        int score = breaking * 100 + warning * 10 + additive;

        return new DriftResult(violations, score);
    }
}
