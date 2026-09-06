# Cloud Run deployment

The API is a request-driven Cloud Run service. The Kafka consumer is a Cloud Run worker pool so it can continuously pull from Kafka.

Before deployment create/private-connect the production Kafka, Redis/Memorystore and OpenSearch services, give the Cloud Run identity permission to call Vertex AI, and configure the external Application Load Balancer + serverless NEG + Cloud Armor policy.

Run `deploy-cloud-run.sh` after setting the required environment variables. The API intentionally uses `--allow-unauthenticated` at the Cloud Run IAM layer because OAuth2/JWT is validated by Spring Security; ingress is restricted to the load balancer/internal path so the `run.app` endpoint cannot bypass Cloud Armor.
