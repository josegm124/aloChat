# Secretos Operativos

## Regla
Ningun token, bot token, webhook secret o API key se guarda en el repositorio.

## Web
En este MVP, AWS Secrets Manager se usa para el canal Web.

## Convencion multi-tenant para Web
Cada tenant de Web debe tener su propio secreto HMAC.

Nombre recomendado:
`alochat/dev/tenants/{tenantId}/channels/web/ingress-signing-key`

JSON sugerido:
```json
{
  "hmacKey": "REEMPLAZAR_CON_LLAVE_LARGA_ALEATORIA"
}
```

## Uso en runtime
`inbound-adapter` lee:
- `alochat.web.secret-prefix`
- `alochat.web.secret-suffix`
- `alochat.aws.region`

Y valida:
- `x-tenant-id`
- `x-signature`
- `x-timestamp`

## Telegram
Telegram queda como canal confiable en este MVP. El bot token no se resuelve desde Secrets Manager por esta decision de proyecto.

## Otros secretos previstos
- `alochat/dev/tenants/{tenantId}/channels/web/ingress-signing-key`
- `alochat/dev/tenants/{tenantId}/channels/telegram/bot-token` si mas adelante se endurece Telegram
- `alochat/dev/tenants/{tenantId}/bedrock/app-config` si llega a ser necesario
- `alochat/dev/tenants/{tenantId}/meta/app-secret` cuando Meta entre al producto

## Practica obligatoria
- rotar cualquier secreto que haya sido compartido por chat, correo o commit
- nunca poner secretos en fixtures
- nunca poner secretos en CloudFormation como texto plano
- aislar secretos por tenant y por canal
