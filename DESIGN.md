# Distributed Document Search Service - Architecture Design

## 1. Requirements

### Functional

- Ingest, update and delete documents for multiple tenants.
- Search document title/content with relevance ranking and filters.
- Combine exact lexical retrieval and semantic retrieval using hybrid search.
- Enforce tenant isolation for every read/write operation.
- Accept asynchronous indexing and expose a stable REST API.

### Non-functional

- 10M+ searchable documents.
- 1,000+ concurrent searches/second with p95 below 500 ms.
- Horizontal scaling for query and indexing workloads.
- Fault tolerance across application and search nodes.
- OAuth2 authentication, OWASP edge protection and API rate limiting.
- Near-real-time search consistency with observable freshness and error budgets.

## 2. Architecture and search design

```text
Client
  |
  v
Global External Application Load Balancer
  |
Cloud Armor (OWASP WAF + edge rate protection)
  |
Serverless NEG
  |
Cloud Run API (OAuth2/JWT, stateless, autoscaled)
  |                 |                         |
  |                 |                         |
Redis            Vertex AI                OpenSearch
rate limits      query embedding           pooled/dedicated indexes
                  |                         |
                  +---- Hybrid Query -------+
                       BM25 + k-NN
                       RRF search pipeline

Indexing:
Cloud Run API -> durable document/event -> Kafka -> Cloud Run Worker Pool
                                             -> embedding -> OpenSearch Bulk API
                                             -> DLT after retries
```

**Security and tenancy.** Spring Security validates OAuth2 bearer tokens. The service derives `tenant_id` from a trusted JWT claim rather than accepting an arbitrary tenant value from the caller. Cloud Armor supplies preconfigured WAF controls and coarse edge throttling; Redis enforces per-tenant application quotas. In pooled OpenSearch indexes, `tenant_id` is both a mandatory non-scoring filter and the custom routing key. Routing reduces shard scatter/gather; it is not treated as an authorization mechanism.

**Hybrid retrieval.** BM25 is retained for exact identifiers, names and rare lexical terms. Dense embeddings address vocabulary mismatch such as `automobile` versus `car`. Documents are embedded during asynchronous indexing; the query is embedded synchronously. OpenSearch stores vectors in a `knn_vector` field using Faiss/HNSW with cosine similarity. The query contains a BM25 clause and a filtered k-NN clause. OpenSearch's RRF ranker combines their result positions:

`RRF(d) = sum(1 / (k + rank_i(d)))`

A direct weighted addition of raw BM25 and vector scores is avoided because the two scores have unrelated distributions/scales. Score normalization is another valid design, but RRF is a simple baseline that does not require score calibration. The initial rank constant is 60 and should be tuned only against a relevance judgment set.

## 3. Data flow, scalability and operations

**Indexing flow.** `POST /v1/documents` validates identity, persists the durable source/metadata in production, publishes a versioned Kafka event and returns `202 Accepted`. Consumers independently scale in a Cloud Run worker pool, generate a `RETRIEVAL_DOCUMENT` embedding and send batched OpenSearch writes. Kafka processing is at least once; stable document IDs, tenant routing and external versions make retries idempotent. Persistent failures go to a dead-letter topic. OpenSearch remains a derived searchable representation and can be rebuilt from the durable source/event history.

**Search flow.** The API validates JWT, resolves tenant placement, applies the Redis quota, obtains a `RETRIEVAL_QUERY` embedding, then executes one routed hybrid query. Both BM25 and k-NN clauses carry the tenant/document filters. OpenSearch runs the lexical and vector candidates and performs RRF between query and fetch phases. The embedding endpoint and OpenSearch are deployed close to the API; repeated hot query embeddings may be cached in Redis. Dependency timeouts are kept below the external 500 ms budget.

**Scalability.** Cloud Run API instances are stateless and scale independently from indexers. Kafka partitions absorb write bursts and permit multiple consumers. OpenSearch primary shards distribute storage/query work; replicas provide failover and additional searchable copies. Tenant routing limits fan-out. Shard count is selected from measured indexed bytes and load tests rather than document count alone. Small tenants share pooled indexes; large/hot or regulated tenants move to dedicated indexes/clusters through a tenant-placement catalog. This prevents one large tenant from forcing a one-index-per-tenant model for all customers.

**Consistency and cache.** Search is near-real-time/eventually consistent: a Kafka-acknowledged document may not be searchable until embedding, indexing and refresh complete. Redis is used for rate limits, tenant placement/query-embedding caching and only selective hot-result caching. OpenSearch/Lucene caches and the OS page cache handle repeated search structures.

**Observability and objectives.** Cloud Logging, Cloud Monitoring, Trace/OpenTelemetry and Actuator/Micrometer cover API latency/errors, OAuth failures, throttling, embedding latency/errors, Kafka producer/consumer failures and lag, DLT rate, OpenSearch query/index latency, vector/BM25 query timing, cluster health, JVM/disk and Redis health. Alerts should use SLO error-budget burn as well as hard dependency thresholds.

| Objective | SLI | Target |
|---|---|---|
| Search latency | End-to-end `/v1/search` duration | p95 <= 500 ms; internal objective <= 450 ms |
| Availability | Successful eligible requests / total | SLO 99.95%; SLA 99.9% monthly |
| Index freshness | Accepted document becomes searchable | 99% within 10 seconds |
| Search errors | 5xx / eligible search requests | < 0.1% steady state |

**API surface.** `POST /v1/documents` -> `202`; `DELETE /v1/documents/{id}?version=n` -> `202`; `POST /v1/search` -> ranked top-K; `GET /actuator/health` -> health/readiness. Result size is bounded; deep pagination should use PIT + `search_after` rather than large offsets.
