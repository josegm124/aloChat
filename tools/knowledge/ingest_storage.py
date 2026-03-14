import argparse
import configparser
import datetime
import hashlib
import hmac
import json
import os
import re
from dataclasses import dataclass
from pathlib import Path

import urllib3
from urllib.parse import urlparse


@dataclass
class ProductDoc:
    item_id: str
    product_name: str
    category: str
    price_mxn: float
    usage: str
    tenant_id: str
    source_version: str

    def to_json(self) -> str:
        return json.dumps(
            {
                "itemId": self.item_id,
                "productName": self.product_name,
                "category": self.category,
                "priceMxn": self.price_mxn,
                "usage": self.usage,
                "tenantId": self.tenant_id,
                "sourceVersion": self.source_version,
            }
        )


def parse_inventory(path: str, tenant_id: str, source_version: str) -> list[ProductDoc]:
    docs: list[ProductDoc] = []
    with open(path, encoding="utf-8") as stream:
        lines = [line.rstrip("\n") for line in stream if line.strip()]

    idx = 0
    while idx < len(lines):
        line = lines[idx]
        if not line or line.startswith("=") or line.startswith("-"):
            idx += 1
            continue

        if "|" not in line:
            idx += 1
            continue

        parts = [part.strip() for part in line.split("|")]
        if len(parts) < 4:
            idx += 1
            continue

        item_id = parts[0]
        if item_id.lower().startswith("item"):
            idx += 1
            continue
        product_name = parts[1]
        category = parts[2]
        price_text = parts[3]
        price_digits = re.sub(r"[^\d.]", "", price_text)
        price_value = float(price_digits) if price_digits else 0.0
        usage = ""
        if idx + 1 < len(lines) and "USO:" in lines[idx + 1]:
            usage = lines[idx + 1].split("USO:", 1)[-1].strip()
            idx += 1

        docs.append(
            ProductDoc(
                item_id=item_id,
                product_name=product_name,
                category=category,
                price_mxn=price_value,
                usage=usage,
                tenant_id=tenant_id,
                source_version=source_version,
            )
        )

        idx += 1

    return docs


def build_bulk_payload(docs: list[ProductDoc], index: str) -> str:
    lines = []
    for doc in docs:
        meta = json.dumps({"index": {"_index": index}})
        lines.append(meta)
        lines.append(doc.to_json())
    return "\n".join(lines) + "\n"


def load_credentials():
    access_key = os.environ.get("AWS_ACCESS_KEY_ID")
    secret_key = os.environ.get("AWS_SECRET_ACCESS_KEY")
    session_token = os.environ.get("AWS_SESSION_TOKEN")
    if access_key and secret_key:
        return access_key, secret_key, session_token

    profile = os.environ.get("AWS_PROFILE", "default")
    credentials_path = Path.home() / ".aws" / "credentials"
    if credentials_path.exists():
        parser = configparser.ConfigParser()
        parser.read(credentials_path)
        if profile in parser:
            section = parser[profile]
            return (
                section.get("aws_access_key_id"),
                section.get("aws_secret_access_key"),
                section.get("aws_session_token"),
            )

    metadata_uri = os.environ.get("AWS_CONTAINER_CREDENTIALS_FULL_URI")
    if not metadata_uri:
        relative = os.environ.get("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI")
        if relative:
            metadata_uri = f"http://169.254.170.2{relative}"

    if metadata_uri:
        http = urllib3.PoolManager()
        response = http.request("GET", metadata_uri, timeout=urllib3.Timeout(connect=2.0, read=5.0))
        if response.status == 200:
            payload = json.loads(response.data.decode())
            return payload.get("AccessKeyId"), payload.get("SecretAccessKey"), payload.get("Token")

    raise SystemExit(
        "Missing AWS credentials in environment, ~/.aws/credentials, or container metadata service."
    )


def sign_and_send(endpoint: str, region: str, body: str, method: str = "POST") -> str:
    access_key, secret_key, session_token = load_credentials()

    service = "aoss"
    parsed = urlparse(endpoint)
    host = parsed.netloc
    canonical_uri = parsed.path or "/"
    amz_date = datetime.datetime.utcnow().strftime("%Y%m%dT%H%M%SZ")
    date_stamp = amz_date[:8]
    payload_hash = hashlib.sha256(body.encode("utf-8")).hexdigest()

    canonical_headers = (
        f"content-type:application/json\nhost:{host}\nx-amz-content-sha256:{payload_hash}\n"
        f"x-amz-date:{amz_date}\n"
    )
    signed_headers = "content-type;host;x-amz-content-sha256;x-amz-date"
    if session_token:
        canonical_headers += f"x-amz-security-token:{session_token}\n"
        signed_headers += ";x-amz-security-token"
    canonical_request = f"{method}\n{canonical_uri}\n\n{canonical_headers}\n{signed_headers}\n{payload_hash}"

    credential_scope = f"{date_stamp}/{region}/{service}/aws4_request"
    string_to_sign = f"AWS4-HMAC-SHA256\n{amz_date}\n{credential_scope}\n{hashlib.sha256(canonical_request.encode('utf-8')).hexdigest()}"

    def sign(key, msg):
        return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()

    k_date = sign(("AWS4" + secret_key).encode("utf-8"), date_stamp)
    k_region = sign(k_date, region)
    k_service = sign(k_region, service)
    k_signing = sign(k_service, "aws4_request")
    signature = hmac.new(k_signing, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()

    authorization_header = (
        f"AWS4-HMAC-SHA256 Credential={access_key}/{credential_scope}, "
        f"SignedHeaders={signed_headers}, Signature={signature}"
    )

    headers = {
        "Content-Type": "application/json",
        "Host": host,
        "X-Amz-Date": amz_date,
        "Authorization": authorization_header,
    }
    if session_token:
        headers["X-Amz-Security-Token"] = session_token
    headers["X-Amz-Content-Sha256"] = payload_hash

    http = urllib3.PoolManager()
    response = http.request(
        method,
        endpoint,
        body=body.encode("utf-8"),
        headers=headers,
        timeout=urllib3.Timeout(connect=10.0, read=60.0),
    )
    if response.status >= 300:
        raise SystemExit(f"Upload failed: {response.status} {response.data.decode()}")
    return response.data.decode()


def main() -> None:
    parser = argparse.ArgumentParser(description="Prepare storage inventory batch for OpenSearch Serverless.")
    parser.add_argument("inventory", help="Path to the storage inventory TXT file.")
    parser.add_argument("--endpoint", required=True, help="OpenSearch collection endpoint (https://...aoss.amazonaws.com).")
    parser.add_argument("--index", default="kb_corpus", help="Target OpenSearch index.")
    parser.add_argument("--region", default="us-east-1", help="AWS region.")
    parser.add_argument("--tenant", default="acme", help="Tenant id to tag documents.")
    parser.add_argument("--source-version", default="storage_inventory_50items_2026.txt", help="Source version metadata.")
    args = parser.parse_args()

    if not os.path.isfile(args.inventory):
        raise SystemExit(f"Inventory file not found: {args.inventory}")

    docs = parse_inventory(args.inventory, args.tenant, args.source_version)
    if not docs:
        raise SystemExit("No documents parsed from inventory file.")

    payload = build_bulk_payload(docs, args.index)
    endpoint = f"{args.endpoint.rstrip('/')}/{args.index}/_bulk"
    response = sign_and_send(endpoint, args.region, payload)

    print("Indexed documents:", len(docs))
    print(response)


if __name__ == "__main__":
    main()
