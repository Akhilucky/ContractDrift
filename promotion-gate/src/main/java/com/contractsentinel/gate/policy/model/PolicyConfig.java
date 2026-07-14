package com.contractsentinel.gate.policy.model;

import java.util.List;

public class PolicyConfig {
    private List<Policy> policies;

    public PolicyConfig() {
    }

    public PolicyConfig(List<Policy> policies) {
        this.policies = policies;
    }

    public List<Policy> getPolicies() {
        return policies;
    }

    public void setPolicies(List<Policy> policies) {
        this.policies = policies;
    }
}
