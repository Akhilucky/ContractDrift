package com.contractsentinel.gate.policy.model;

import java.util.List;

public class Policy {
    private String name;
    private String description;
    private List<Condition> conditions;
    private String action;

    public Policy() {
    }

    public Policy(String name, String description, List<Condition> conditions, String action) {
        this.name = name;
        this.description = description;
        this.conditions = conditions;
        this.action = action;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
