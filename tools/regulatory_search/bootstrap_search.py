#!/usr/bin/env python3
import argparse
import datetime
import hashlib
import hmac
import json
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

EMBEDDING_MODEL_ID = "amazon.titan-embed-text-v2:0"
EMBEDDING_DIMENSIONS = 1024


INDEX_DEFINITIONS = {
    "regulatory_corpus": {
        "settings": {
            "index": {
                "knn": True,
            }
        },
        "mappings": {
            "properties": {
                "framework": {"type": "keyword"},
                "jurisdiction": {"type": "keyword"},
                "language": {"type": "keyword"},
                "sector": {"type": "keyword"},
                "pillar": {"type": "keyword"},
                "control_id": {"type": "keyword"},
                "title": {"type": "text"},
                "summary": {"type": "text"},
                "obligations": {"type": "text"},
                "keywords": {"type": "keyword"},
                "source_url": {"type": "keyword"},
                "source_reference": {"type": "text"},
                "embedding": {
                    "type": "knn_vector",
                    "dimension": 1024,
                    "method": {
                        "engine": "faiss",
                        "space_type": "cosinesimil",
                        "name": "hnsw",
                    },
                },
            }
        },
    },
    "control_profiles": {
        "mappings": {
            "properties": {
                "sector": {"type": "keyword"},
                "profile_id": {"type": "keyword"},
                "risk_level": {"type": "keyword"},
                "frameworks": {"type": "keyword"},
                "high_risk_triggers": {"type": "text"},
                "required_evidence": {"type": "text"},
                "source_references": {"type": "text"},
            }
        }
    },
    "evidence_index": {
        "settings": {
            "index": {
                "knn": True,
            }
        },
        "mappings": {
            "properties": {
                "assessment_id": {"type": "keyword"},
                "artifact_id": {"type": "keyword"},
                "framework": {"type": "keyword"},
                "pillar": {"type": "keyword"},
                "content": {"type": "text"},
                "source_reference": {"type": "text"},
                "embedding": {
                    "type": "knn_vector",
                    "dimension": 1024,
                    "method": {
                        "engine": "faiss",
                        "space_type": "cosinesimil",
                        "name": "hnsw",
                    },
                },
            }
        },
    },
}


def load_jsonl(path: Path):
    docs = []
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line:
            continue
        docs.append(json.loads(line))
    return docs


def get_aws_config_value(key: str) -> str:
    result = subprocess.run(
        ["aws", "configure", "get", key],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return ""
    return result.stdout.strip()


def signed_http(method: str, url: str, region: str, body=None, headers=None, service: str = "aoss"):
    access_key = get_aws_config_value("aws_access_key_id")
    secret_key = get_aws_config_value("aws_secret_access_key")
    session_token = get_aws_config_value("aws_session_token")
    if not access_key or not secret_key:
        raise RuntimeError("AWS shared credentials are required for OpenSearch Serverless ingestion")
    payload = body if isinstance(body, str) else (body.decode("utf-8") if body else "")
    parsed = urllib.parse.urlparse(url)
    amz_date = datetime.datetime.now(datetime.UTC).strftime("%Y%m%dT%H%M%SZ")
    date_stamp = amz_date[:8]
    host = parsed.netloc
    canonical_uri = parsed.path or "/"
    canonical_querystring = parsed.query
    payload_hash = hashlib.sha256(payload.encode("utf-8")).hexdigest()

    canonical_headers = {
        "host": host,
        "x-amz-content-sha256": payload_hash,
        "x-amz-date": amz_date,
    }
    for key, value in (headers or {}).items():
        canonical_headers[key.lower()] = value.strip()
    if session_token:
        canonical_headers["x-amz-security-token"] = session_token

    signed_header_names = sorted(canonical_headers.keys())
    canonical_headers_block = "".join(f"{name}:{canonical_headers[name]}\n" for name in signed_header_names)
    signed_headers = ";".join(signed_header_names)

    canonical_request = "\n".join(
        [
            method,
            canonical_uri,
            canonical_querystring,
            canonical_headers_block,
            signed_headers,
            payload_hash,
        ]
    )

    credential_scope = f"{date_stamp}/{region}/{service}/aws4_request"
    string_to_sign = "\n".join(
        [
            "AWS4-HMAC-SHA256",
            amz_date,
            credential_scope,
            hashlib.sha256(canonical_request.encode("utf-8")).hexdigest(),
        ]
    )

    def sign(key_bytes, message):
        return hmac.new(key_bytes, message.encode("utf-8"), hashlib.sha256).digest()

    signing_key = sign(
        sign(
            sign(
                sign(f"AWS4{secret_key}".encode("utf-8"), date_stamp),
                region,
            ),
            service,
        ),
        "aws4_request",
    )
    signature = hmac.new(signing_key, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()
    authorization = (
        f"AWS4-HMAC-SHA256 Credential={access_key}/{credential_scope}, "
        f"SignedHeaders={signed_headers}, Signature={signature}"
    )

    request_headers = {key: value for key, value in (headers or {}).items()}
    request_headers["Authorization"] = authorization
    request_headers["x-amz-content-sha256"] = payload_hash
    request_headers["x-amz-date"] = amz_date
    if session_token:
        request_headers["x-amz-security-token"] = session_token

    request = urllib.request.Request(
        url=url,
        data=payload.encode("utf-8") if payload else None,
        headers=request_headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8")


def wait_for_collection(endpoint: str, region: str, timeout_seconds: int = 300):
    started = time.time()
    health_url = urllib.parse.urljoin(endpoint, "/")
    while time.time() - started < timeout_seconds:
        status, _ = signed_http("GET", health_url, region)
        if status in (200, 401, 403, 404):
            return
        time.sleep(10)
    raise TimeoutError(f"Collection endpoint {endpoint} did not become ready in time")


def ensure_index(endpoint: str, region: str, index_name: str, body: dict):
    url = urllib.parse.urljoin(endpoint, f"/{index_name}")
    status, payload = signed_http("PUT", url, region, body=json.dumps(body), headers={"content-type": "application/json"})
    if status not in (200, 201):
        if status == 400 and "resource_already_exists_exception" in payload:
            print(f"index {index_name} already exists")
            return
        raise RuntimeError(f"failed to create index {index_name}: status={status} payload={payload}")
    print(f"created index {index_name}")


def delete_index(endpoint: str, region: str, index_name: str):
    url = urllib.parse.urljoin(endpoint, f"/{index_name}")
    status, payload = signed_http("DELETE", url, region)
    if status in (200, 202, 404):
        print(f"deleted index {index_name}" if status != 404 else f"index {index_name} not present")
        return
    raise RuntimeError(f"failed to delete index {index_name}: status={status} payload={payload}")


def bulk_ingest(endpoint: str, region: str, index_name: str, docs):
    if not docs:
        print(f"no documents for {index_name}")
        return
    lines = []
    for doc in docs:
        lines.append(json.dumps({"index": {"_index": index_name}}))
        lines.append(json.dumps(doc))
    payload = "\n".join(lines) + "\n"
    url = urllib.parse.urljoin(endpoint, "/_bulk")
    status, body = signed_http(
        "POST",
        url,
        region,
        body=payload,
        headers={"content-type": "application/x-ndjson"},
    )
    if status not in (200, 201):
        raise RuntimeError(f"bulk ingest failed for {index_name}: status={status} body={body}")
    response = json.loads(body)
    if response.get("errors"):
        raise RuntimeError(f"bulk ingest reported errors for {index_name}: {body}")
    print(f"ingested {len(docs)} documents into {index_name}")

def invoke_embedding(text: str, region: str, model_id: str, dimensions: int, normalize: bool):
    payload = json.dumps({
        "inputText": text,
        "dimensions": dimensions,
        "normalize": normalize,
    })
    with tempfile.NamedTemporaryFile(mode="r+", encoding="utf-8") as output_file:
        result = subprocess.run(
            [
                "aws",
                "bedrock-runtime",
                "invoke-model",
                "--region",
                region,
                "--model-id",
                model_id,
                "--content-type",
                "application/json",
                "--accept",
                "application/json",
                "--body",
                payload,
                "--cli-binary-format",
                "raw-in-base64-out",
                output_file.name,
            ],
            capture_output=True,
            text=True,
        )
        output_file.seek(0)
        response_body = output_file.read().strip()
    if result.returncode != 0:
        raise RuntimeError(f"bedrock embedding failed: {result.stderr.strip()}")
    response = json.loads(response_body)
    embedding = response.get("embedding")
    if not embedding:
        raise RuntimeError("bedrock embedding response did not include an embedding")
    return embedding


def build_embedding_text(doc: dict) -> str:
    def flatten(value):
        if isinstance(value, list):
            return " ".join(str(item) for item in value)
        return str(value) if value else ""

    parts = [
        flatten(doc.get("framework", "")),
        flatten(doc.get("sector", "")),
        flatten(doc.get("pillar", "")),
        flatten(doc.get("title", "")),
        flatten(doc.get("summary", "")),
        flatten(doc.get("obligations", "")),
        flatten(doc.get("keywords", "")),
        flatten(doc.get("source_reference", "")),
    ]
    return " ".join(part for part in parts if part).strip()


def attach_embeddings(docs, region: str, model_id: str, dimensions: int, normalize: bool):
    embedded_docs = []
    for doc in docs:
        enriched = dict(doc)
        if "embedding" not in enriched:
            enriched["embedding"] = invoke_embedding(build_embedding_text(doc), region, model_id, dimensions, normalize)
        embedded_docs.append(enriched)
    return embedded_docs


def parse_args():
    parser = argparse.ArgumentParser(description="Bootstrap alo regulatory search collection")
    parser.add_argument("--endpoint", required=True, help="OpenSearch Serverless collection endpoint")
    parser.add_argument("--region", default="us-east-1")
    parser.add_argument(
        "--corpus-root",
        default=str(Path(__file__).resolve().parent / "corpus"),
        help="Directory containing JSONL corpus files",
    )
    parser.add_argument("--embedding-model-id", default=EMBEDDING_MODEL_ID)
    parser.add_argument("--embedding-dimensions", type=int, default=EMBEDDING_DIMENSIONS)
    parser.add_argument("--normalize-embeddings", action=argparse.BooleanOptionalAction, default=True)
    parser.add_argument("--skip-embeddings", action="store_true")
    return parser.parse_args()


def main():
    args = parse_args()
    endpoint = args.endpoint.rstrip("/")
    corpus_root = Path(args.corpus_root)
    wait_for_collection(endpoint, args.region)

    for index_name, definition in INDEX_DEFINITIONS.items():
        delete_index(endpoint, args.region, index_name)
        ensure_index(endpoint, args.region, index_name, definition)

    regulatory_corpus = load_jsonl(corpus_root / "regulatory_corpus.jsonl")
    if not args.skip_embeddings:
        regulatory_corpus = attach_embeddings(
            regulatory_corpus,
            args.region,
            args.embedding_model_id,
            args.embedding_dimensions,
            args.normalize_embeddings,
        )

    bulk_ingest(
        endpoint,
        args.region,
        "regulatory_corpus",
        regulatory_corpus,
    )
    bulk_ingest(
        endpoint,
        args.region,
        "control_profiles",
        load_jsonl(corpus_root / "control_profiles.jsonl"),
    )
    print("search bootstrap completed")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # pragma: no cover
        print(str(exc), file=sys.stderr)
        sys.exit(1)
