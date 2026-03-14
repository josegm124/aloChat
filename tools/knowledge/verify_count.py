import json
import sys

from ingest_storage import sign_and_send


def main() -> None:
    endpoint = "https://1ta56cm25m7q4g4e4oxd.us-east-1.aoss.amazonaws.com"
    index = "kb_corpus"
    if len(sys.argv) > 1:
        index = sys.argv[1]

    body = json.dumps({"size": 0})
    response = sign_and_send(f"{endpoint}/{index}/_search", "us-east-1", body)
    print(response)


if __name__ == "__main__":
    main()
