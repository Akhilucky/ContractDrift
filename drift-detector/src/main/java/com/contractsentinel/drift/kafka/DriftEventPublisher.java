package com.contractsentinel.drift.kafka;

import com.contractsentinel.drift.model.DriftEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DriftEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DriftEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final ObjectMapper objectMapper;

    public DriftEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                               @Value("${drift.kafka.topic:contract-violations}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void publish(DriftEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.contractId().toString(), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish DriftEvent for contract {}: {}",
                                    event.contractId(), ex.getMessage());
                        } else {
                            log.debug("Published DriftEvent for contract {} to topic {}",
                                    event.contractId(), topic);
                        }
                    });
        } catch (Exception e) {
            log.error("Error serializing DriftEvent for contract {}: {}",
                    event.contractId(), e.getMessage());
        }
    }
}
