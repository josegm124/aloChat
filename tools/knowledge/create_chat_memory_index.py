import json

from ingest_storage import sign_and_send


def main() -> None:
    endpoint = "https://1ta56cm25m7q4g4e4oxd.us-east-1.aoss.amazonaws.com"
    index = "chat_memory"

    body = {
        "mappings": {
            "properties": {
                "memoryKey": {"type": "keyword"},
                "tenantId": {"type": "keyword"},
                "userId": {"type": "keyword"},
                "conversationId": {"type": "keyword"},
                "summary": {"type": "text"},
                "products": {"type": "keyword"},
                "tags": {"type": "keyword"},
                "updatedAt": {"type": "date"},
            }
        }
    }

    print(sign_and_send(f"{endpoint}/{index}", "us-east-1", json.dumps(body), method="PUT"))


if __name__ == "__main__":
    main()
