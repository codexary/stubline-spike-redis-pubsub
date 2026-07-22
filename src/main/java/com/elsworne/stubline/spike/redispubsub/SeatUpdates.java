package com.elsworne.stubline.spike.redispubsub;

import java.util.List;
import java.util.Map;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.params.XReadGroupParams;

/** Publishes ephemeral counts and appends the same counts to a retained stream. */
public final class SeatUpdates {
  public static final String CHANNEL = "seats:concert:live";
  public static final String STREAM = "seats:concert:history";
  private final String host;
  private final int port;

  public SeatUpdates(String host, int port) {
    this.host = host;
    this.port = port;
  }

  public void publish(int remaining) {
    try (var jedis = new Jedis(host, port)) {
      jedis.publish(CHANNEL, Integer.toString(remaining));
      jedis.xadd(STREAM, StreamEntryID.NEW_ENTRY, Map.of("remaining", Integer.toString(remaining)));
    }
  }

  public void createGroup(String group) {
    try (var jedis = new Jedis(host, port)) {
      try {
        jedis.xgroupCreate(STREAM, group, new StreamEntryID("0-0"), true);
      } catch (redis.clients.jedis.exceptions.JedisDataException e) {
        if (!e.getMessage().contains("BUSYGROUP")) throw e;
      }
    }
  }

  public List<Map.Entry<String, List<redis.clients.jedis.resps.StreamEntry>>> read(
      String group, String consumer) {
    try (var jedis = new Jedis(host, port)) {
      return jedis.xreadGroup(
          group,
          consumer,
          XReadGroupParams.xReadGroupParams().count(10),
          Map.of(STREAM, StreamEntryID.UNRECEIVED_ENTRY));
    }
  }

  public long ack(String group, StreamEntryID id) {
    try (var jedis = new Jedis(host, port)) {
      return jedis.xack(STREAM, group, id);
    }
  }
}
