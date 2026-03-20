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
from urllib.parse import urlparse

import urllib3


@dataclass
class ProductRecord:
    item_id: str
    product_name: str
    category: str
    price_mxn: float
    usage: str
    tenant_id: str
    source_version: str
    unit: str = ""


@dataclass
class KnowledgeDoc:
    doc_id: str
    item_id: str
    product_name: str
    category: str
    usage: str
    tenant_id: str
    source_version: str
    language: str
    doc_type: str
    unit: str = ""
    price_mxn: float | None = None
    regular_price_mxn: float | None = None
    promo_price_mxn: float | None = None
    promotion_type: str = ""
    offer_title: str = ""
    offer_description: str = ""
    valid_from: str = ""
    valid_until: str = ""
    offer_active: bool = False
    search_text: str = ""

    def to_json(self) -> str:
        payload = {
            "docKey": self.doc_id,
            "itemId": self.item_id,
            "productName": self.product_name,
            "category": self.category,
            "usage": self.usage,
            "tenantId": self.tenant_id,
            "sourceVersion": self.source_version,
            "language": self.language,
            "docType": self.doc_type,
            "unit": self.unit,
            "searchText": self.search_text,
        }
        if self.price_mxn is not None:
            payload["priceMxn"] = self.price_mxn
        if self.regular_price_mxn is not None:
            payload["regularPriceMxn"] = self.regular_price_mxn
        if self.promo_price_mxn is not None:
            payload["promoPriceMxn"] = self.promo_price_mxn
        if self.promotion_type:
            payload["promotionType"] = self.promotion_type
        if self.offer_title:
            payload["offerTitle"] = self.offer_title
        if self.offer_description:
            payload["offerDescription"] = self.offer_description
        if self.valid_from:
            payload["validFrom"] = self.valid_from
        if self.valid_until:
            payload["validUntil"] = self.valid_until
        if self.offer_active:
            payload["offerActive"] = True
        return json.dumps(payload, ensure_ascii=False)


def parse_inventory(path: str, tenant_id: str, source_version: str) -> list[ProductRecord]:
    docs: list[ProductRecord] = []
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
        unit = parts[4] if len(parts) >= 5 and "USO:" not in parts[4] else ""
        usage = parts[5] if len(parts) >= 6 else ""
        if not usage and idx + 1 < len(lines) and "USO:" in lines[idx + 1]:
            usage = lines[idx + 1].split("USO:", 1)[-1].strip()
            idx += 1

        docs.append(
            ProductRecord(
                item_id=item_id,
                product_name=product_name,
                category=category,
                price_mxn=price_value,
                usage=usage,
                tenant_id=tenant_id,
                source_version=source_version,
                unit=unit,
            )
        )

        idx += 1

    return docs


def parse_languages(raw_languages: str) -> list[str]:
    languages: list[str] = []
    for language in raw_languages.split(","):
        normalized = language.strip().lower()
        if normalized in {"es", "en"} and normalized not in languages:
            languages.append(normalized)
    if not languages:
        raise SystemExit("At least one supported language is required. Use es and/or en.")
    return languages


def build_product_documents(products: list[ProductRecord], languages: list[str]) -> list[KnowledgeDoc]:
    docs: list[KnowledgeDoc] = []
    for product in products:
        for language in languages:
            localized_category = localize_text(product.category, language)
            localized_usage = localize_text(product.usage, language)
            search_text = " ".join(
                value for value in [
                    product.product_name,
                    localized_category,
                    localized_usage,
                    "paint coating finish recommendation wall roof metal wood"
                    if language == "en"
                    else "pintura recubrimiento acabado recomendacion pared techo metal madera",
                ] if value
            )
            docs.append(
                KnowledgeDoc(
                    doc_id=f"product::{product.item_id}::{language}",
                    item_id=product.item_id,
                    product_name=product.product_name,
                    category=localized_category,
                    usage=localized_usage,
                    tenant_id=product.tenant_id,
                    source_version=product.source_version,
                    language=language,
                    doc_type="product",
                    unit=localize_unit(product.unit, language),
                    price_mxn=product.price_mxn,
                    search_text=search_text,
                )
            )
    return docs


def parse_offers(
    path: str,
    tenant_id: str,
    source_version: str,
    products: list[ProductRecord],
    languages: list[str],
) -> list[KnowledgeDoc]:
    if not path:
        return []
    if not os.path.isfile(path):
        raise SystemExit(f"Offers file not found: {path}")

    with open(path, encoding="utf-8") as stream:
        payload = json.load(stream)
    if not isinstance(payload, list):
        raise SystemExit("Offers file must contain a JSON array.")

    products_by_id = {product.item_id: product for product in products}
    products_by_name = {product.product_name.lower(): product for product in products}
    docs: list[KnowledgeDoc] = []
    for raw_offer in payload:
        if not isinstance(raw_offer, dict):
            continue
        item_id = str(raw_offer.get("itemId") or "").strip()
        product_name = str(raw_offer.get("productName") or "").strip()
        base_product = products_by_id.get(item_id) or products_by_name.get(product_name.lower())
        if base_product is None:
            continue

        regular_price = read_float(raw_offer.get("regularPriceMxn"), base_product.price_mxn)
        promo_price = read_float(raw_offer.get("promoPriceMxn"), regular_price)
        valid_from = str(raw_offer.get("validFrom") or "").strip()
        valid_until = str(raw_offer.get("validUntil") or "").strip()
        promotion_type = str(raw_offer.get("promotionType") or raw_offer.get("offerType") or "promotion").strip()
        offer_active = is_offer_active(valid_from, valid_until)

        for language in languages:
            offer_title = resolve_offer_text(raw_offer, language, "title")
            if not offer_title:
                offer_title = default_offer_title(base_product.product_name, promotion_type, language)
            offer_description = resolve_offer_text(raw_offer, language, "description")
            if not offer_description:
                offer_description = default_offer_description(
                    base_product.product_name,
                    regular_price,
                    promo_price,
                    valid_until,
                    language,
                )
            localized_category = localize_text(base_product.category, language)
            localized_usage = localize_text(base_product.usage, language)
            search_text = " ".join(
                value for value in [
                    base_product.product_name,
                    localized_category,
                    localized_usage,
                    offer_title,
                    offer_description,
                    promotion_type,
                    "discount offer sale promotion deal"
                    if language == "en"
                    else "descuento oferta promocion rebaja",
                ] if value
            )
            docs.append(
                KnowledgeDoc(
                    doc_id=f"offer::{base_product.item_id}::{promotion_type or 'promo'}::{language}",
                    item_id=base_product.item_id,
                    product_name=base_product.product_name,
                    category=localized_category,
                    usage=localized_usage,
                    tenant_id=tenant_id,
                    source_version=source_version,
                    language=language,
                    doc_type="offer",
                    unit=localize_unit(base_product.unit, language),
                    price_mxn=promo_price if offer_active else regular_price,
                    regular_price_mxn=regular_price,
                    promo_price_mxn=promo_price,
                    promotion_type=promotion_type,
                    offer_title=offer_title,
                    offer_description=offer_description,
                    valid_from=valid_from,
                    valid_until=valid_until,
                    offer_active=offer_active,
                    search_text=search_text,
                )
            )
    return docs


def resolve_offer_text(raw_offer: dict, language: str, field_prefix: str) -> str:
    candidates = [
        f"{field_prefix}_{language}",
        f"{field_prefix}{language.upper()}",
        f"{field_prefix}{language.capitalize()}",
        f"{field_prefix}Es" if language == "es" else f"{field_prefix}En",
    ]
    if field_prefix == "title":
        candidates.extend([
            "offerTitle" if language == "es" else "offerTitleEn",
            "offerTitleEs" if language == "es" else "offerTitleEn",
        ])
    if field_prefix == "description":
        candidates.extend([
            "offerDescription" if language == "es" else "offerDescriptionEn",
            "offerDescriptionEs" if language == "es" else "offerDescriptionEn",
        ])
    for candidate in candidates:
        value = raw_offer.get(candidate)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ""


def read_float(value, default: float) -> float:
    if value is None:
        return default
    if isinstance(value, (int, float)):
        return float(value)
    digits = re.sub(r"[^\d.]", "", str(value))
    return float(digits) if digits else default


def is_offer_active(valid_from: str, valid_until: str) -> bool:
    today = datetime.date.today()
    from_date = parse_iso_date(valid_from)
    until_date = parse_iso_date(valid_until)
    if from_date and today < from_date:
        return False
    if until_date and today > until_date:
        return False
    return bool(from_date or until_date)


def parse_iso_date(value: str) -> datetime.date | None:
    if not value:
        return None
    try:
        return datetime.date.fromisoformat(value)
    except ValueError:
        return None


def default_offer_title(product_name: str, promotion_type: str, language: str) -> str:
    normalized_type = promotion_type.strip().lower()
    if language == "en":
        return f"{product_name} promotion ({normalized_type or 'offer'})"
    return f"Promocion de {product_name} ({normalized_type or 'oferta'})"


def default_offer_description(
    product_name: str,
    regular_price: float,
    promo_price: float,
    valid_until: str,
    language: str,
) -> str:
    valid_until_suffix = f" until {valid_until}" if language == "en" and valid_until else ""
    valid_until_suffix_es = f" hasta {valid_until}" if language == "es" and valid_until else ""
    if language == "en":
        return (
            f"{product_name} is available at a promotional price of {promo_price:.2f} MXN "
            f"instead of {regular_price:.2f} MXN{valid_until_suffix}."
        )
    return (
        f"{product_name} esta disponible con precio promocional de {promo_price:.2f} MXN "
        f"en lugar de {regular_price:.2f} MXN{valid_until_suffix_es}."
    )


def localize_unit(unit: str, language: str) -> str:
    normalized = safe_text(unit).lower()
    if not normalized:
        return ""
    if language == "es":
        return unit
    return {
        "cubeta": "bucket",
        "galon": "gallon",
        "litro": "liter",
        "pieza": "piece",
    }.get(normalized, unit)


def localize_text(value: str, language: str) -> str:
    text = safe_text(value)
    if language == "es" or not text:
        return text

    replacements = [
        ("Vinil-Acrilica Premium", "Premium vinyl-acrylic"),
        ("Vinil-Acrilica Estandar", "Standard vinyl-acrylic"),
        ("Acrilica Exterior", "Exterior acrylic"),
        ("Sellador Pintura Concreto", "Concrete paint sealer"),
        ("Tinta para madera", "Wood stain"),
        ("Madera Exterior", "Exterior wood"),
        ("Preparacion", "Preparation"),
        ("Decorativo", "Decorative"),
        ("Pintura en Polvo", "Powder coating"),
        ("Acrilico elastomerico", "Elastomeric acrylic"),
        ("Acrilico Deportivo", "Sports acrylic"),
        ("Poliuretano Techo", "Roof polyurethane"),
        ("Pintura para casa", "Paint for a house"),
        ("Pintar", "Paint"),
        ("pintar", "paint"),
        ("interiores", "interiors"),
        ("interior", "interior"),
        ("casas", "houses"),
        ("casa", "house"),
        ("oficinas", "offices"),
        ("alto trafico", "high-traffic"),
        ("Muros interiores", "Interior walls"),
        ("fachadas residenciales", "residential facades"),
        ("Fachadas", "Facades"),
        ("fachadas", "facades"),
        ("climas humedos", "humid climates"),
        ("muy soleados", "very sunny areas"),
        ("muros de concreto nuevos", "new concrete walls"),
        ("superficies con salitre", "surfaces with salt damage"),
        ("Proteccion", "Protection"),
        ("herreria", "metalwork"),
        ("puertas de metal", "metal doors"),
        ("barandales", "railings"),
        ("Evitar filtraciones", "Prevent leaks"),
        ("azoteas", "rooftops"),
        ("edificios", "buildings"),
        ("habitaciones", "rooms"),
        ("pared", "wall"),
        ("paredes", "walls"),
        ("muro", "wall"),
        ("muros", "walls"),
        ("techo", "roof"),
        ("techos", "roofs"),
        ("madera", "wood"),
        ("bano", "bathroom"),
        ("baño", "bathroom"),
        ("cocina", "kitchen"),
        ("humedad", "humidity"),
        ("Impermeabilizacion", "Waterproofing"),
        ("de alta gama", "premium-grade"),
        ("quirofanos", "operating rooms"),
        ("elimina bacterias al contacto", "helps eliminate bacteria on contact"),
    ]

    localized = text
    for source, target in replacements:
        localized = localized.replace(source, target)
    return localized


def build_bulk_payload(docs: list[KnowledgeDoc], index: str) -> str:
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


def safe_text(value: str | None) -> str:
    return value.strip() if isinstance(value, str) else ""


def main() -> None:
    parser = argparse.ArgumentParser(description="Prepare storage inventory batch for OpenSearch Serverless.")
    parser.add_argument("inventory", help="Path to the storage inventory TXT or PSV file.")
    parser.add_argument("--endpoint", required=True, help="OpenSearch collection endpoint (https://...aoss.amazonaws.com).")
    parser.add_argument("--index", default="kb_corpus", help="Target OpenSearch index.")
    parser.add_argument("--region", default="us-east-1", help="AWS region.")
    parser.add_argument("--tenant", default="acme", help="Tenant id to tag documents.")
    parser.add_argument("--source-version", default="storage_inventory_50items_2026.txt", help="Source version metadata.")
    parser.add_argument("--languages", default="es,en", help="Comma separated languages to index. Supported: es,en.")
    parser.add_argument("--offers-file", default="", help="Optional JSON array with active offers/promotions.")
    args = parser.parse_args()

    if not os.path.isfile(args.inventory):
        raise SystemExit(f"Inventory file not found: {args.inventory}")

    languages = parse_languages(args.languages)
    products = parse_inventory(args.inventory, args.tenant, args.source_version)
    if not products:
        raise SystemExit("No documents parsed from inventory file.")

    docs = build_product_documents(products, languages)
    docs.extend(parse_offers(args.offers_file, args.tenant, args.source_version, products, languages))

    payload = build_bulk_payload(docs, args.index)
    endpoint = f"{args.endpoint.rstrip('/')}/{args.index}/_bulk"
    response = sign_and_send(endpoint, args.region, payload)

    print("Indexed documents:", len(docs))
    print(response)


if __name__ == "__main__":
    main()
