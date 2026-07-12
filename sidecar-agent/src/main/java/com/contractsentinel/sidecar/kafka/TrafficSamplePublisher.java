package com.contractsentinel.sidecar.kafka;

import com.contractsentinel.sidecar.model.TrafficSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class TrafficSamplePublisher {

    private static final Logger log = LoggerFactory.getLogger(TrafficSamplePublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public TrafficSamplePublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${kafka.topic.raw-traffic:raw-traffic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(TrafficSample sample) {
        try {
            String key = sample.serviceId() + ":" + sample.endpoint();
            String value = serializeSample(sample);

            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, value);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish traffic sample for endpoint={}, statusCode={}",
                            sample.endpoint(), sample.statusCode(), ex);
                } else {
                    log.debug("Published traffic sample for endpoint={}, statusCode={}, offset={}",
                            sample.endpoint(), sample.statusCode(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            log.error("Error serializing and publishing traffic sample for endpoint={}",
                    sample.endpoint(), e);
        }
    }

    private String serializeSample(TrafficSample sample) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(sample);
        } catch (Exception e) {
            log.error("Failed to serialize traffic sample", e);
            return "{\"error\":\"serialization_failed\",\"serviceId\":\"" +
                    sample.serviceId() + "\",\"endpoint\":\"" + sample.endpoint() + "\"}";
        }
    }
}
