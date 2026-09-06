package com.example.search.service;

import com.example.search.controller.SearchRequest;
import com.example.search.controller.SearchResponse;
import com.example.search.model.DocumentEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
public class OpenSearchService {
    private static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson");

    private final RestClient client;
    private final ObjectMapper mapper;
    private final EmbeddingService embeddings;
    private final String index;
    private final String rrfPipeline;
    private final String searchTimeout;
    private final int primaryShards;
    private final int replicas;
    private final int vectorCandidates;

    public OpenSearchService(
            RestClient openSearchClient,
            ObjectMapper mapper,
            EmbeddingService embeddings,
            @Value("${app.opensearch.index:documents-v2}") String index,
            @Value("${app.opensearch.rrf-pipeline:document-search-rrf}") String rrfPipeline,
            @Value("${app.opensearch.search-timeout:320ms}") String searchTimeout,
            @Value("${app.opensearch.primary-shards:4}") int primaryShards,
            @Value("${app.opensearch.replicas:1}") int replicas,
            @Value("${app.opensearch.vector-candidates:50}") int vectorCandidates) {
        this.client = openSearchClient;
        this.mapper = mapper;
        this.embeddings = embeddings;
        this.index = index;
        this.rrfPipeline = rrfPipeline;
        this.searchTimeout = searchTimeout;
        this.primaryShards = primaryShards;
        this.replicas = replicas;
        this.vectorCandidates = vectorCandidates;
    }

    public void initialize() {
        ensureRrfPipeline();
        ensureIndex();
    }

    public void bulkIndex(List<DocumentEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        StringBuilder ndjson = new StringBuilder();
        try {
            for (DocumentEvent event : events) {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("_index", index);
                meta.put("_id", event.documentId());
                meta.put("routing", event.tenantId());
                meta.put("version", event.version());
                meta.put("version_type", "external_gte");

                if (event.operation() == DocumentEvent.Operation.DELETE) {
                    ndjson.append(mapper.writeValueAsString(Map.of("delete", meta))).append('\n');
                    continue;
                }

                List<Float> vector = embeddings.documentEmbedding(event.title(), event.content());
                ndjson.append(mapper.writeValueAsString(Map.of("index", meta))).append('\n');

                Map<String, Object> source = new LinkedHashMap<>();
                source.put("tenant_id", event.tenantId());
                source.put("document_id", event.documentId());
                source.put("title", event.title());
                source.put("content", event.content());
                source.put("document_type", value(event.documentType()));
                source.put("department", value(event.department()));
                source.put("version", event.version());
                source.put("updated_at", event.updatedAt().toString());
                source.put("embedding", vector);
                ndjson.append(mapper.writeValueAsString(source)).append('\n');
            }
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize bulk request", ex);
        }

        JsonNode response = client.post()
                .uri("/_bulk")
                .contentType(NDJSON)
                .body(ndjson.toString())
                .retrieve()
                .body(JsonNode.class);

        if (response != null && response.path("errors").asBoolean(false)) {
            throw new IllegalStateException("OpenSearch bulk request contained failures");
        }
    }

    public SearchResponse search(String tenantId, SearchRequest request) {
        List<Float> queryVector = embeddings.queryEmbedding(request.query());
        List<Object> filters = filters(tenantId, request);

        Map<String, Object> lexical = Map.of(
                "bool", Map.of(
                        "filter", filters,
                        "must", List.of(Map.of(
                                "multi_match", Map.of(
                                        "query", request.query(),
                                        "fields", List.of("title^3", "content"),
                                        "type", "best_fields")))));

        Map<String, Object> semantic = Map.of(
                "knn", Map.of(
                        "embedding", Map.of(
                                "vector", queryVector,
                                "k", Math.max(vectorCandidates, request.limitOrDefault()),
                                "filter", Map.of("bool", Map.of("filter", filters)))));

        Map<String, Object> body = Map.of(
                "size", request.limitOrDefault(),
                "track_total_hits", true,
                "timeout", searchTimeout,
                "_source", Map.of("excludes", List.of("embedding")),
                "query", Map.of(
                        "hybrid", Map.of(
                                "queries", List.of(lexical, semantic))));

        JsonNode root = client.post()
                .uri(uriBuilder -> uriBuilder.path("/{index}/_search")
                        .queryParam("routing", tenantId)
                        .queryParam("search_pipeline", rrfPipeline)
                        .build(index))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (root == null) {
            return new SearchResponse(0, 0, List.of());
        }

        long took = root.path("took").asLong();
        long total = root.path("hits").path("total").path("value").asLong();
        List<SearchResponse.Hit> hits = new ArrayList<>();
        for (JsonNode hit : root.path("hits").path("hits")) {
            Map<String, Object> source = mapper.convertValue(hit.path("_source"), Map.class);
            hits.add(new SearchResponse.Hit(
                    hit.path("_id").asText(),
                    hit.path("_score").asDouble(),
                    source));
        }
        return new SearchResponse(took, total, hits);
    }

    private List<Object> filters(String tenantId, SearchRequest request) {
        List<Object> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("tenant_id", tenantId)));
        if (request.documentType() != null && !request.documentType().isBlank()) {
            filters.add(Map.of("term", Map.of("document_type", request.documentType())));
        }
        if (request.department() != null && !request.department().isBlank()) {
            filters.add(Map.of("term", Map.of("department", request.department())));
        }
        return filters;
    }

    private void ensureRrfPipeline() {
        Map<String, Object> pipeline = Map.of(
                "description", "RRF fusion for lexical and semantic search",
                "phase_results_processors", List.of(Map.of(
                        "score-ranker-processor", Map.of(
                                "combination", Map.of(
                                        "technique", "rrf",
                                        "rank_constant", 60)))));

        client.put()
                .uri("/_search/pipeline/{pipeline}", rrfPipeline)
                .contentType(MediaType.APPLICATION_JSON)
                .body(pipeline)
                .retrieve()
                .toBodilessEntity();
    }

    private void ensureIndex() {
        try {
            client.head().uri("/{index}", index).retrieve().toBodilessEntity();
            return;
        } catch (HttpClientErrorException.NotFound ignored) {
        }

        Map<String, Object> vectorField = Map.of(
                "type", "knn_vector",
                "dimension", embeddings.dimension(),
                "method", Map.of(
                        "name", "hnsw",
                        "space_type", "cosinesimil",
                        "engine", "faiss",
                        "parameters", Map.of(
                                "ef_construction", 128,
                                "m", 16)));

        Map<String, Object> body = Map.of(
                "settings", Map.of(
                        "index", Map.of(
                                "knn", true,
                                "number_of_shards", primaryShards,
                                "number_of_replicas", replicas,
                                "refresh_interval", "1s")),
                "mappings", Map.of(
                        "_routing", Map.of("required", true),
                        "properties", Map.of(
                                "tenant_id", Map.of("type", "keyword"),
                                "document_id", Map.of("type", "keyword"),
                                "title", Map.of("type", "text"),
                                "content", Map.of("type", "text"),
                                "document_type", Map.of("type", "keyword"),
                                "department", Map.of("type", "keyword"),
                                "version", Map.of("type", "long"),
                                "updated_at", Map.of("type", "date"),
                                "embedding", vectorField)));

        try {
            client.put()
                    .uri("/{index}", index)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.BadRequest ex) {
            if (!ex.getResponseBodyAsString().contains("resource_already_exists_exception")) {
                throw ex;
            }
        }
    }

    private static String value(String input) {
        return input == null ? "" : input;
    }
}
