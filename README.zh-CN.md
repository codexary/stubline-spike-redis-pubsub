[English](README.md) | 简体中文

Compose 将无认证的 Redis 绑定到本机回环地址，仅供本地演示客户端访问。

# 实时座位数：Redis pub/sub 与 Streams

先运行 `./mvnw -q verify` 和 `docker compose up -d`。在两个终端运行 `./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.redispubsub.RedisUpdatesMain -Dexec.args='subscribe'`。在第三个终端运行相同命令，但改为 `-Dexec.args='publish 9'`。两个订阅者都会打印 `remaining=9`。**之后**启动的订阅者收不到 `9`。发布 `8` 后，三个订阅者都会打印 `remaining=8`。用 `-Dexec.args='subscribe 1000'` 启动慢订阅者，并快速发布多个更新：它约每秒打印一条，而快订阅者继续及时接收。极慢的订阅者最终可能因 Redis 输出缓冲区限制而断开；pub/sub 不提供重放。

比较 stream：发布后运行 `./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.redispubsub.RedisUpdatesMain -Dexec.args='stream views viewer-1'`。它打印历史 stream ID 和座位数，包括启动前的更新，并逐条以 `XACK` 确认。换一个 group 名可以独立读取同一份历史。`./mvnw -q -Pdocker verify` 测试扇出、迟到订阅者、慢订阅者、stream 历史和确认。最后运行 `docker compose down`。

| | Pub/sub | 带 consumer group 的 Streams |
|---|---|---|
| 交付 | 向当前监听者即发即弃 | 条目被存储，由 group 的 consumer 读取 |
| 迟到监听者 | 错过之前的消息 | 从 group 起始 ID 读取保留的历史 |
| 确认 | 无 | `XACK` 从 group 待处理列表移除条目 |
| 适用 | 暂态实时 UI 更新 | 需要追赶、重放和恢复的工作 |

## 许可证

本仓库采用 [MIT License](LICENSE)。

## 关于本仓库

本仓库包含一项针对 Stubline 的独立实验。README 说明运行命令与观察结果。

本仓库的代码与文档借助 AI 工具编写；记录的输出都来自实际运行。

## 结论

实验结果及其对后端决策的影响见[FINDINGS.zh-CN.md](FINDINGS.zh-CN.md)。
