package com.example.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class EmbeddingService {
    private static final String CLOUD_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final String provider;
    private final String projectId;
    private final String location;
    private final String model;
    private final int dimension;
    private final int maxChars;
    private final RestClient vertexClient;
    private GoogleCredentials credentials;

    public EmbeddingService(
            @Value("${app.embedding.provider:local}") String provider,
            @Value("${app.embedding.project-id:}") String projectId,
            @Value("${app.embedding.location:us-central1}") String location,
            @Value("${app.embedding.model:gemini-embedding-001}") String model,
            @Value("${app.embedding.dimension:768}") int dimension,
            @Value("${app.embedding.max-chars:12000}") int maxChars) {
        this.provider = provider;
        this.projectId = projectId;
        this.location = location;
        this.model = model;
        this.dimension = dimension;
        this.maxChars = maxChars;
        this.vertexClient = RestClient.builder()
                .baseUrl("https://" + location + "-aiplatform.googleapis.com")
                .build();
    }

    public List<Float> documentEmbedding(String title, String content) {
        String text = title + "\n" + content;
        return embed(text, "RETRIEVAL_DOCUMENT");
    }

    public List<Float> queryEmbedding(String query) {
        return embed(query, "RETRIEVAL_QUERY");
    }

    public int dimension() {
        return dimension;
    }

    private List<Float> embed(String text, String taskType) {
        if ("vertex".equalsIgnoreCase(provider)) {
            return vertexEmbedding(text, taskType);
        }
        return localEmbedding(text);
    }

    private List<Float> vertexEmbedding(String text, String taskType) {
        if (!StringUtils.hasText(projectId)) {
            throw new IllegalStateException("EMBEDDING_PROJECT_ID is required for Vertex embeddings");
        }

        var body = java.util.Map.of(
                "instances", List.of(java.util.Map.of(
                        "content", trim(text),
                        "task_type", taskType)),
                "parameters", java.util.Map.of(
                        "outputDimensionality", dimension,
                        "autoTruncate", true));

        JsonNode response = vertexClient.post()
                .uri("/v1/projects/{project}/locations/{location}/publishers/google/models/{model}:predict",
                        projectId, location, model)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        JsonNode values = response == null
                ? null
                : response.path("predictions").path(0).path("embeddings").path("values");
        if (values == null || !values.isArray() || values.size() != dimension) {
            throw new IllegalStateException("Embedding service returned an unexpected response");
        }

        List<Float> vector = new ArrayList<>(dimension);
        values.forEach(value -> vector.add((float) value.asDouble()));
        return vector;
    }

    private synchronized String accessToken() {
        try {
            if (credentials == null) {
                credentials = GoogleCredentials.getApplicationDefault().createScoped(CLOUD_SCOPE);
            }
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not obtain Google Cloud credentials", ex);
        }
    }

    private List<Float> localEmbedding(String text) {
        float[] vector = new float[dimension];
        String[] tokens = trim(text).toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int hash = token.hashCode();
            int index = Math.floorMod(hash, dimension);
            vector[index] += (hash & 1) == 0 ? 1f : -1f;
        }

        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            vector[0] = 1f;
            norm = 1;
        }

        float scale = (float) Math.sqrt(norm);
        List<Float> result = new ArrayList<>(dimension);
        for (float value : vector) {
            result.add(value / scale);
        }
        return result;
    }

    private String trim(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }
}
