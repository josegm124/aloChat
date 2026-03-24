#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8080}"

curl -sS -X POST "$API_BASE/api/v1/assessments" \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "tenant-demo",
    "organizationId": "org-demo",
    "userId": "user-demo",
    "preferredLanguage": "ES",
    "sector": "HEALTHCARE",
    "useCaseType": "clinical-triage",
    "aiSystemCategory": "diagnostic-support",
    "geography": "ES",
    "datasetProvided": true,
    "systemName": "Alo Health Audit",
    "systemVersion": "0.1.0",
    "provider": "Alo",
    "deploymentContext": "hospital",
    "artifacts": [
      {
        "artifactType": "DOCUMENT_PDF",
        "fileName": "technical-dossier.pdf",
        "s3Bucket": "alo-local-uploads",
        "s3Key": "assessments/demo/technical-dossier.pdf",
        "checksum": "doc-demo-sha256",
        "contentType": "application/pdf",
        "sizeBytes": 102400,
        "uploadedAt": "2026-03-23T12:00:00Z",
        "metadata": {
          "source": "sample"
        }
      },
      {
        "artifactType": "DATASET_CSV",
        "fileName": "clinical-dataset.csv",
        "s3Bucket": "alo-local-uploads",
        "s3Key": "assessments/demo/clinical-dataset.csv",
        "checksum": "dataset-demo-sha256",
        "contentType": "text/csv",
        "sizeBytes": 204800,
        "uploadedAt": "2026-03-23T12:00:05Z",
        "metadata": {
          "source": "sample"
        }
      }
    ],
    "intakeAnswers": {
      "usesPersonalData": "true",
      "usesSensitiveData": "true",
      "humanOversight": "true"
    }
  }'

echo
