package com.elsworne.stubline.spike.redispubsub;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

/** Redis pub/sub timing and stream history/ack tests. */
@Tag("docker")
@EnabledIfSystemProperty(named = "run.docker", matches = "true")
class SeatUpdatesDockerTest {
  private static GenericContainer<?> redis() {
    var redis =
        new GenericContainer<>(DockerImageName.parse("redis:8.6.7-alpine3.23"))
            .withExposedPorts(6379);
    redis.start();
    return redis;
  }

  private static final class Listener extends JedisPubSub {
    final CountDownLatch subscribed = new CountDownLatch(1);
    final List<String> values = new CopyOnWriteArrayList<>();
    final long delay;

    Listener(long delay) {
      this.delay = delay;
    }

    @Override
    public void onSubscribe(String channel, int count) {
      subscribed.countDown();
    }

    @Override
    public void onMessage(String channel, String message) {
      if (delay > 0)
        try {
          Thread.sleep(delay);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      values.add(message);
    }
  }

  private static void listen(String host, int port, Listener listener) {
    try (var jedis = new Jedis(host, port)) {
      jedis.subscribe(listener, SeatUpdates.CHANNEL);
    }
  }

  private static void untilSize(Listener listener, int expected) throws Exception {
    for (int i = 0; i < 30 && listener.values.size() < expected; i++) Thread.sleep(50);
    assertEquals(expected, listener.values.size());
  }

  @Test
  void manyListenersAndLateArrival() throws Exception {
    try (var redis = redis();
        var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
      var updates = new SeatUpdates(redis.getHost(), redis.getMappedPort(6379));
      var first = new Listener(0);
      var second = new Listener(0);
      tasks.submit(() -> listen(redis.getHost(), redis.getMappedPort(6379), first));
      tasks.submit(() -> listen(redis.getHost(), redis.getMappedPort(6379), second));
      assertTrue(first.subscribed.await(2, TimeUnit.SECONDS));
      assertTrue(second.subscribed.await(2, TimeUnit.SECONDS));
      updates.publish(9);
      untilSize(first, 1);
      untilSize(second, 1);
      var late = new Listener(0);
      tasks.submit(() -> listen(redis.getHost(), redis.getMappedPort(6379), late));
      assertTrue(late.subscribed.await(2, TimeUnit.SECONDS));
      assertTrue(late.values.isEmpty());
      updates.publish(8);
      untilSize(first, 2);
      untilSize(second, 2);
      untilSize(late, 1);
      assertEquals("8", late.values.get(0));
      first.unsubscribe();
      second.unsubscribe();
      late.unsubscribe();
    }
  }

  @Test
  void slowListenerDoesNotHoldFastListener() throws Exception {
    try (var redis = redis();
        var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
      var updates = new SeatUpdates(redis.getHost(), redis.getMappedPort(6379));
      var slow = new Listener(500);
      var fast = new Listener(0);
      tasks.submit(() -> listen(redis.getHost(), redis.getMappedPort(6379), slow));
      tasks.submit(() -> listen(redis.getHost(), redis.getMappedPort(6379), fast));
      assertTrue(slow.subscribed.await(2, TimeUnit.SECONDS));
      assertTrue(fast.subscribed.await(2, TimeUnit.SECONDS));
      updates.publish(7);
      updates.publish(6);
      untilSize(fast, 2);
      assertTrue(slow.values.size() < 2);
      untilSize(slow, 2);
      slow.unsubscribe();
      fast.unsubscribe();
    }
  }

  @Test
  void streamRetainsHistoryUntilGroupReadsAndAcknowledges() throws Exception {
    try (var redis = redis()) {
      var updates = new SeatUpdates(redis.getHost(), redis.getMappedPort(6379));
      updates.publish(9);
      updates.publish(8);
      updates.createGroup("views");
      var entries = updates.read("views", "viewer-1");
      assertEquals(2, entries.get(0).getValue().size());
      for (var entry : entries.get(0).getValue())
        assertEquals(1, updates.ack("views", entry.getID()));
      assertTrue(
          updates.read("views", "viewer-1") == null || updates.read("views", "viewer-1").isEmpty());
      try (var jedis = new Jedis(redis.getHost(), redis.getMappedPort(6379))) {
        assertEquals(2, jedis.xlen(SeatUpdates.STREAM));
      }
    }
  }
}
