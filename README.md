# Distributed Document Search

Spring Boot prototype for a multi-tenant distributed document search service.

The implementation keeps the repository small but covers the main assignment concerns: asynchronous indexing, hybrid lexical/semantic retrieval, tenant isolation, OAuth2/JWT, rate limiting, horizontal scaling and failure handling.

## Search approach

Each indexed document has two searchable representations:

- `title` and `content` are indexed by Lucene for BM25 lexical search.
- `embedding` is stored as an OpenSearch `knn_vector` using Faiss/HNSW and cosine similarity.

At query time the service creates a query embedding and sends one OpenSearch `hybrid` query containing:

1. BM25 `multi_match` over `title^3` and `content`.
2. k-NN search over the dense `embedding` field.

OpenSearch fuses both result lists using a search pipeline with Reciprocal Rank Fusion (RRF). RRF uses rank positions rather than adding raw BM25 and vector scores, which avoids mixing unrelated score scales.

Tenant isolation is applied to both retrieval paths. `tenant_id` is a keyword filter and also the OpenSearch routing key, so a tenant query avoids unnecessary shard fan-out.

## Repository layout

```text
src/main/java/com/example/search
  controller/       REST API and request/response models
  service/          search, embeddings, Kafka and OpenSearch logic
  security/         tenant context from JWT
  ratelimit/        Redis per-tenant rate limiting
  config/           Spring Security, Kafka and HTTP client configuration
```

The same image runs in two roles:

- `api`: Cloud Run service for document APIs and search.
- `indexer`: Cloud Run worker pool consuming Kafka and writing OpenSearch.

## Local run

Docker is the only requirement:

```bash
docker compose up --build
```

Local mode disables OAuth and uses `X-Tenant-Id`. It also uses a deterministic local embedding implementation so the complete vector/RRF plumbing runs without external credentials. It is meant for functional testing, not semantic quality measurement.

For real semantic behavior set `EMBEDDING_PROVIDER=vertex` and configure Application Default Credentials. Production uses Vertex AI `gemini-embedding-001` with separate `RETRIEVAL_DOCUMENT` and `RETRIEVAL_QUERY` task types.

Index a document:

```bash
curl -X POST http://localhost:8080/v1/documents \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: acme' \
  -d '{
    "documentId":"doc-1",
    "title":"Remote Work Policy",
    "content":"Employees may work remotely up to three days per week.",
    "documentType":"POLICY",
    "department":"HR",
    "version":1
  }'
```

Search:

```bash
curl -s -X POST http://localhost:8080/v1/search \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: acme' \
  -d '{"query":"remote work rules","limit":10}'
```

Delete:

```bash
curl -X DELETE 'http://localhost:8080/v1/documents/doc-1?version=2' \
  -H 'X-Tenant-Id: acme'
```

Health:

```bash
curl http://localhost:8080/actuator/health
```

## Production configuration

The API is intended to sit behind:

```text
Global External Application Load Balancer
        -> Cloud Armor
        -> serverless NEG
        -> Cloud Run API
```

Spring Security validates OAuth2/JWT tokens and derives `tenant_id` from a trusted claim. Cloud Armor provides edge WAF/rate protection; Redis provides per-tenant application quotas. Restrict Cloud Run ingress to load-balancer/internal traffic.

Production embedding settings:

```text
EMBEDDING_PROVIDER=vertex
EMBEDDING_PROJECT_ID=<gcp-project>
EMBEDDING_LOCATION=us-central1
EMBEDDING_MODEL=gemini-embedding-001
EMBEDDING_DIMENSION=768
```

The Cloud Run identity needs permission to call Vertex AI.

## Indexing semantics

`POST /v1/documents` publishes a versioned Kafka event and returns `202 Accepted`. Kafka delivery to the indexer is at least once. OpenSearch writes use a stable document ID, tenant routing and external versions, making retries idempotent and preventing older document versions from overwriting newer ones.

Failed Kafka records retry with backoff and are then sent to `<topic>.DLT`.

## Load test

`load/k6-search.js` drives a constant 1,000 search requests/second and checks the assignment's p95 target:

```bash
k6 run load/k6-search.js
```

For a meaningful production benchmark use a real embedding provider, representative data size, tenant skew, query mix and concurrent indexing. Measure both retrieval quality and latency; hybrid search adds embedding and ANN work to the critical path.
