# GitHub Actions CI/CD para `dev`

Este documento resume la automatizacion actual del repo para `CI` y `CD` hacia AWS `dev`.

## 1. Workflows reales en repo

- `/.github/workflows/ci.yml`
- `/.github/workflows/cd-dev.yml`

## 2. CI

Trigger:
- `pull_request`
- `push` a `master`

Pasos:
1. checkout
2. Java 21
3. `./gradlew test`
4. `./gradlew build -x test`

Objetivo:
- validar que el monorepo compile
- validar pruebas minimas antes de merge o deploy

## 3. CD Dev

Trigger:
- `push` a `master`
- `workflow_dispatch`

Pasos:
1. checkout
2. Java 21
3. asumir role AWS con OIDC
4. login a ECR
5. construir/publicar 4 imagenes con `jib`
6. desplegar `infra/runtime.yaml` por CloudFormation
7. esperar servicios ECS estables
8. imprimir `HttpApiUrl`

Servicios publicados:
- `inbound-adapter`
- `processor`
- `ai-orchestrator`
- `outbound-dispatcher`

## 4. Configuracion necesaria en GitHub

Environment:
- `dev`

Variables:
- `AWS_REGION`
- `ECR_REGISTRY`
- `AWS_ROLE_ARN`

Secrets:
- `TELEGRAM_BOT_TOKEN`

## 5. Configuracion necesaria en AWS

Elementos:
- OIDC provider `token.actions.githubusercontent.com`
- role IAM `github-actions-alochat-dev`
- policies de ECR, CloudFormation, ECS read y runtime extra para deploy del stack

## 6. Trust policy correcta

Punto critico del proyecto:
- `cd-dev.yml` usa `environment: dev`
- por eso el `sub` del token OIDC no va por rama, va por environment

Trust policy correcta:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::668778694151:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:josegm124/aloChat:environment:dev"
        }
      }
    }
  ]
}
```

Error tipico si se configura mal:
- `Not authorized to perform sts:AssumeRoleWithWebIdentity`

Causa tipica:
- dejar el `sub` con `repo:josegm124/aloChat:ref:refs/heads/master`

## 7. Como se activa

Automatico:
- `CI` en PR y push a `master`
- `CD Dev` en push a `master`

Manual:
- `GitHub -> Actions -> CD Dev -> Run workflow`

## 8. Nota operativa de bootstrap

Si el repo se empuja por HTTPS y el commit modifica `/.github/workflows/*`, GitHub puede rechazar el push si el Personal Access Token no tiene permisos de workflows.

Opciones:
- usar PAT con permisos de workflows
- o usar SSH con write access sobre el repo
