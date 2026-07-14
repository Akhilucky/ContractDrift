package com.contractsentinel.gate.policy.model;

public class Condition {
    private String severity;
    private int minCount;
    private String window;

    public Condition() {
    }

    public Condition(String severity, int minCount, String window) {
        this.severity = severity;
        this.minCount = minCount;
        this.window = window;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public int getMinCount() {
        return minCount;
    }

    public void setMinCount(int minCount) {
        this.minCount = minCount;
    }

    public String getWindow() {
        return window;
    }

    public void setWindow(String window) {
        this.window = window;
    }
}
