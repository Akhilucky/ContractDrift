package com.contractsentinel.gate.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PrometheusMetricsConfig {

    @Bean
    public Counter gateDecisionsCounter(MeterRegistry registry) {
        return Counter.builder("sentinel_gate_decisions_total")
                .description("Total number of gate decisions")
                .tag("decision", "allow")
                .register(registry);
    }

    @Bean
    public Counter gateDenyCounter(MeterRegistry registry) {
        return Counter.builder("sentinel_gate_decisions_total")
                .description("Total number of denied gate decisions")
                .tag("decision", "deny")
                .register(registry);
    }

    @Bean
    public Counter gateOverrideCounter(MeterRegistry registry) {
        return Counter.builder("sentinel_gate_decisions_total")
                .description("Total number of override gate decisions")
                .tag("decision", "override")
                .register(registry);
    }
}
