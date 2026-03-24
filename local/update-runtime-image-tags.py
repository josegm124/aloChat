#!/usr/bin/env python3
import json
import sys
from pathlib import Path


SERVICE_KEYS = {
    "IntakeImageUri": "alo-intake-service",
    "ProfileImageUri": "alo-profile-service",
    "DocumentImageUri": "alo-document-compliance-service",
    "DatasetImageUri": "alo-dataset-compliance-service",
    "OrchestratorImageUri": "alo-assessment-orchestrator",
    "ReportImageUri": "alo-report-service",
    "NotificationImageUri": "alo-notification-service",
    "RetryRouterImageUri": "alo-retry-router-service",
    "MskAdminImageUri": "alo-msk-admin",
}


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: update-runtime-image-tags.py <tag>", file=sys.stderr)
        return 1

    tag = sys.argv[1]
    region = "us-east-1"
    account_id = "668778694151"
    path = Path("infra/parameters/dev/runtime.json")
    data = json.loads(path.read_text())

    for item in data:
        key = item.get("ParameterKey")
        service = SERVICE_KEYS.get(key)
        if service is None:
            continue
        item["ParameterValue"] = (
            f"{account_id}.dkr.ecr.{region}.amazonaws.com/alo/dev/{service}:{tag}"
        )

    path.write_text(json.dumps(data, indent=2) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
