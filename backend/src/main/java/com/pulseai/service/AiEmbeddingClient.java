package com.pulseai.service;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiEmbeddingClient {

    public static final int DIMENSIONS = 384;
    private final RestClient client;
    private final String expectedModel;

    public AiEmbeddingClient(
            @Value("${app.ai-service.url:http://localhost:8000}") String serviceUrl,
            @Value("${app.ai.embedding-model:sentence-transformers/all-MiniLM-L6-v2}") String expectedModel) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        this.client = RestClient.builder()
                .baseUrl(serviceUrl)
                .requestFactory(requestFactory)
                .build();
        this.expectedModel = expectedModel;
    }

    public EmbeddingBatch embed(List<String> texts) {
        EmbeddingBatch response = client.post()
                .uri("/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbeddingRequest(texts))
                .retrieve()
                .body(EmbeddingBatch.class);
        if (response == null || !expectedModel.equals(response.model())
                || response.dimensions() != DIMENSIONS || response.embeddings() == null
                || response.embeddings().size() != texts.size()) {
            throw new IllegalStateException("The embedding service returned an incompatible model response.");
        }
        for (List<Double> vector : response.embeddings()) {
            if (vector == null || vector.size() != DIMENSIONS
                    || vector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                throw new IllegalStateException("The embedding service returned an invalid vector.");
            }
        }
        return response;
    }

    public String modelName() {
        return expectedModel;
    }

    public record EmbeddingRequest(List<String> texts) {}
    public record EmbeddingBatch(String model, int dimensions, List<List<Double>> embeddings) {}
}
