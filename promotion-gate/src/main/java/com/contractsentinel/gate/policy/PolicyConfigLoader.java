package com.contractsentinel.gate.policy;

import com.contractsentinel.gate.policy.model.PolicyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class PolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(PolicyConfig.class);

    @Value("${sentinel.policy.path:classpath:policies.yml}")
    private String policyPath;

    @Bean
    public com.contractsentinel.gate.policy.model.PolicyConfig policyConfig() {
        return loadPolicyConfig();
    }

    private com.contractsentinel.gate.policy.model.PolicyConfig loadPolicyConfig() {
        Yaml yaml = new Yaml();

        try {
            InputStream inputStream;

            if (policyPath.startsWith("classpath:")) {
                String resourcePath = policyPath.replace("classpath:", "");
                inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
                if (inputStream == null) {
                    log.warn("Policy file not found in classpath: {}, using empty config", resourcePath);
                    return new com.contractsentinel.gate.policy.model.PolicyConfig();
                }
            } else {
                Path path = Path.of(policyPath);
                if (!Files.exists(path)) {
                    log.warn("Policy file not found at path: {}, using empty config", policyPath);
                    return new com.contractsentinel.gate.policy.model.PolicyConfig();
                }
                inputStream = Files.newInputStream(path);
            }

            com.contractsentinel.gate.policy.model.PolicyConfig config = yaml.load(inputStream);
            log.info("Loaded {} policies from {}", 
                    config.getPolicies() != null ? config.getPolicies().size() : 0, policyPath);
            return config;
        } catch (IOException e) {
            log.error("Failed to load policy file: {}", policyPath, e);
            return new com.contractsentinel.gate.policy.model.PolicyConfig();
        }
    }
}
