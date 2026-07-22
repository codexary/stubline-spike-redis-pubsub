English | [简体中文](FINDINGS.zh-CN.md)

# Findings

## What we observed

Two active pub/sub subscribers received the published seat count; a subscriber started afterward missed that update. A slow subscriber did not hold up fast ones, but pub/sub provided no replay. The Stream example retained earlier entries for a consumer group and required `XACK` after handling.

## Backend decision

Pub/sub is suitable for disposable live seat counts. Order notifications need retained events and an independent consumer position, which informs the backend outbox and Kafka design. See [backend ADR 0010 — Use outbox and Kafka for order events](https://github.com/codexary/stubline-backend/blob/main/docs/adr/0010-use-outbox-and-kafka-for-order-events.md).
