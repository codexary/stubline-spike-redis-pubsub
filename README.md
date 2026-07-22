English | [简体中文](README.zh-CN.md)

Compose binds unauthenticated Redis to loopback so only local demo clients can reach it.

# Live seat counts: Redis pub/sub and Streams

Build with `./mvnw -q verify`, then run `docker compose up -d`. Start two terminals with `./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.redispubsub.RedisUpdatesMain -Dexec.args='subscribe'`. In a third, run the same command with `-Dexec.args='publish 9'`. Both subscribers print `remaining=9`. Start another subscriber **after** that publish; it receives no `9`. Publish `8`; all three print `remaining=8`. Start a slow subscriber with `-Dexec.args='subscribe 1000'` and publish several updates quickly: it prints each received update about one second apart while fast subscribers continue promptly. A sufficiently slow subscriber may eventually be disconnected by Redis's output buffer limit; pub/sub supplies no replay.

For the stream comparison, run `./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.redispubsub.RedisUpdatesMain -Dexec.args='stream views viewer-1'` after publishing. It prints historical stream IDs and counts, including updates published before it started, then acknowledges each with `XACK`. Run another group name to read the same retained history independently. `./mvnw -q -Pdocker verify` tests fan-out, a late subscriber, a slow subscriber, and stream history plus acknowledgement. Stop with `docker compose down`.

| | Pub/sub | Streams with consumer group |
|---|---|---|
| Delivery | Fire-and-forget to current listeners | Stored entries read by group consumers |
| Late listener | Misses earlier messages | Reads retained history from the group's start ID |
| Acknowledgement | None | `XACK` removes an entry from the group's pending list |
| Fit | Transient live UI updates | Work needing catch-up, replay, and recovery |

## License

This repository is licensed under the [MIT License](LICENSE).

## About this repository

This repository contains a focused Stubline experiment. The README documents its commands and observed results.

The code and documents in this repository were written with the help of AI tools; every recorded output comes from actually running them.
