[English](FINDINGS.md) | 简体中文

# 实验结论

## 观察

两名在线的 pub/sub 订阅者收到座位数更新，之后启动的订阅者收不到先前那条。慢订阅者没有拖住快订阅者，但 pub/sub 不能补读。Stream 示例保留旧条目，consumer group 处理后用 `XACK` 确认。

## 对后端的影响

pub/sub 适合可丢弃的实时座位数。订单通知需要保留事件，并让消费者各自记录进度；这一点支持后端采用 outbox 和 Kafka。参见[后端 ADR 0010：Use outbox and Kafka for order events](https://github.com/codexary/stubline-backend/blob/main/docs/adr/0010-use-outbox-and-kafka-for-order-events.md)。
