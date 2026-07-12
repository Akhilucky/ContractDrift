package com.contractsentinel.correlation.adapter;

import com.contractsentinel.correlation.model.DeploymentEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class GitHubReleaseAdapter {

    private static final Logger log = LoggerFactory.getLogger(GitHubReleaseAdapter.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${github.org}")
    private String org;

    @Value("${github.repo}")
    private String repo;

    @Value("${github.token}")
    private String token;

    @Value("${kafka.topic.deployment-events:deployment-events}")
    private String deploymentEventsTopic;

    public GitHubReleaseAdapter(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.httpClient = HttpClient.newHttpClient();
    }

    @Scheduled(fixedDelayString = "${polling.interval.ms:60000}")
    public void pollReleases() {
        try {
            String url = "https://api.github.com/repos/" + org + "/" + repo + "/releases?per_page=5";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("GitHub API returned status: {}", response.statusCode());
                return;
            }

            JsonNode releases = objectMapper.readTree(response.body());
            for (JsonNode release : releases) {
                String tagName = release.get("tag_name").asText();
                String releaseName = release.get("name").asText();
                String deploymentId = UUID.randomUUID().toString();

                DeploymentEvent event = new DeploymentEvent(
                        deploymentId,
                        repo,
                        tagName,
                        "production",
                        LocalDateTime.now(),
                        "GITHUB_RELEASE",
                        releaseName
                );

                String json = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(deploymentEventsTopic, event.serviceId(), json);
                log.info("Published deployment event for release: {}", tagName);
            }
        } catch (Exception e) {
            log.error("Error polling GitHub releases", e);
        }
    }
}
