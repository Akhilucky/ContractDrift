package com.contractsentinel.drift.model;

public record Violation(
        String path,
        ChangeType changeType,
        String description,
        String classification
) {
    public enum ChangeType {
        BREAKING,
        WARNING,
        ADDITIVE
    }
}
