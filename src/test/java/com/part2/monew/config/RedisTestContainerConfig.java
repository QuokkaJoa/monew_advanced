package com.part2.monew.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

public abstract class RedisTestContainerConfig {

  static final String REDIS_IMAGE = "redis:7-alpine";
  static final GenericContainer<?> REDIS_CONTAINER;

  static {
    REDIS_CONTAINER = new GenericContainer<>(REDIS_IMAGE)
        .withExposedPorts(6379)
        .withReuse(true); // 컨테이너 재사용 옵션
    REDIS_CONTAINER.start();
  }

  @DynamicPropertySource
  public static void overrideProps(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
    registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
     registry.add("redisson.address", () -> "redis://" + REDIS_CONTAINER.getHost() + ":" + REDIS_CONTAINER.getMappedPort(6379));
  }
}
