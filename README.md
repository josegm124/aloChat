# ALO CertifAI

This repository contains the backend and infrastructure side of the CertifAI MVP.

The safest place for local MVP work is `feature/RBSai`.
Do not push unfinished local-only changes to `master`, because `master` is wired to CI/CD.

## Repos

- Backend and infra: `/home/jose-guerrero/Desktop/aloChat`
- Frontend: `/home/jose-guerrero/Desktop/aloFront`

## What works in local MVP mode

Local MVP mode is designed to avoid AWS spend while still validating the product flow:

- create assessment
- upload PDF
- run document analysis
- generate final report
- open web report
- download PDF
- generate notification payload without real email

## Local dependencies

The local stack uses:

- Redpanda
- DynamoDB Local
- MinIO
- Spring Boot services in Docker
- Next.js frontend

## Start the local backend

From this repo:

```bash
bash local/run-local.sh
```

This starts:

- `alo-intake-service` on `localhost:8080`
- `alo-document-compliance-service` on `localhost:8082`
- `alo-report-service` on `localhost:8086`
- `alo-notification-service` on `localhost:8087`
- `DynamoDB Local` on `localhost:8000`
- `MinIO` on `localhost:9000`
- `MinIO Console` on `localhost:9001`
- `Redpanda` on `localhost:19092`

MinIO buckets are created automatically:

- `alo-uploads`
- `alo-reports`

## Start the frontend

From the frontend repo:

```bash
cd /home/jose-guerrero/Desktop/aloFront
ALOCHAT_LOCAL_MODE=true npm run start -- --hostname 127.0.0.1 --port 3000
```

Open:

- `http://127.0.0.1:3000`

## Stop the local backend

From this repo:

```bash
bash local/stop-local.sh
```

## Local validation status

The local MVP path has been validated with a real PDF:

- assessment accepted
- document analysis returned findings
- final report HTML generated
- final PDF generated
- notification returned `LOCAL_DISABLED`

This local mode does not send real email.

## Cloud note

Cloud deployment still exists as a separate path using CloudFormation and AWS-managed services.
That path is not required for day-to-day local MVP development.

## Documentation

- Architecture summary: `ARCHITECTURE.txt`
