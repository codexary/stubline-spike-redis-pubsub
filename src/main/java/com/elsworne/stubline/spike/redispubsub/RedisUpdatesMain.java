package com.elsworne.stubline.spike.redispubsub;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

/** Demo publisher, live subscriber, and stream-group reader. */
public final class RedisUpdatesMain {
  private RedisUpdatesMain() {}

  public static void main(String[] args) throws Exception {
    if (args.length == 0)
      throw new IllegalArgumentException(
          "publish COUNT|subscribe [DELAY_MS]|stream GROUP CONSUMER");
    var updates = new SeatUpdates("localhost", 6379);
    switch (args[0]) {
      case "publish" -> {
        int count = Integer.parseInt(args[1]);
        updates.publish(count);
        System.out.println("published " + count);
      }
      case "subscribe" -> {
        long delay = args.length > 1 ? Long.parseLong(args[1]) : 0;
        try (var jedis = new Jedis("localhost", 6379)) {
          jedis.subscribe(
              new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                  System.out.println("remaining=" + message);
                  try {
                    Thread.sleep(delay);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    unsubscribe();
                  }
                }
              },
              SeatUpdates.CHANNEL);
        }
      }
      case "stream" -> {
        String group = args[1], consumer = args[2];
        updates.createGroup(group);
        while (true) {
          var entries = updates.read(group, consumer);
          if (entries != null)
            for (var stream : entries)
              for (var entry : stream.getValue()) {
                System.out.println("stream " + entry.getID() + " " + entry.getFields());
                updates.ack(group, entry.getID());
              }
          Thread.sleep(200);
        }
      }
      default -> throw new IllegalArgumentException("unknown command");
    }
  }
}
