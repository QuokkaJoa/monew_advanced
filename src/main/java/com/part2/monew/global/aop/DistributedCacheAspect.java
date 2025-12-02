package com.part2.monew.global.aop;

import com.part2.monew.global.annotation.DistributedCache;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class DistributedCacheAspect {

  private final RedissonClient redissonClient;
  private final CacheManager cacheManager;
  private final ExpressionParser parser = new SpelExpressionParser();

  @Around("@annotation(distributedCache)")
  public Object handleCache(ProceedingJoinPoint joinPoint, DistributedCache distributedCache) throws Throwable {
    String cacheKey = generateKey(joinPoint, distributedCache.key());
    String cacheName = distributedCache.cacheName();

    Cache cache = cacheManager.getCache(cacheName);
    if (cache != null) {
      Cache.ValueWrapper wrapper = cache.get(cacheKey);
      Object cacheValue = (wrapper != null) ? wrapper.get() : null;
      if (cacheValue != null) {
        log.info("[Cache Hit] CacheName : {}, Key: {}", cacheName, cacheKey);
        return cacheValue;
      }
    }

    String lockKey = "lock:" + cacheName + ":" + cacheKey;
    RLock lock = redissonClient.getLock(lockKey);

    try {
      boolean isLocked = lock.tryLock(
          distributedCache.waitTime(),
          distributedCache.leaseTime(),
          distributedCache.timeUnit()
      );

      if (!isLocked) {
        log.warn("[Lock Failed] Timeout waiting for lock: {}", lockKey);
        throw new RuntimeException("Server is busy. Please try again later.");
      }

      log.info("[Lock Acquired] Key: {}", lockKey);

      if (cache != null) {
        Object cachedValue = cache.get(cacheKey) != null ? Objects.requireNonNull(
            cache.get(cacheKey)).get() : null;
        if (cachedValue != null) {
          log.info("[Double-Checked Cache Hit] Key: {}", cacheKey);
          return cachedValue;
        }
      }

      Object result = joinPoint.proceed();

      if (cache != null && result != null) {
        cache.put(cacheKey, result);
        log.info("[Cache Put] Key: {}", cacheKey);
      }

      return result;

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Thread interrupted while waiting for lock", e);
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
        log.info("[Lock Released] Key: {}", lockKey);
      }
    }
  }

  private String generateKey(ProceedingJoinPoint joinPoint, String spelKey) {
    if (!StringUtils.hasText(spelKey)) {
      return StringUtils.arrayToDelimitedString(joinPoint.getArgs(), "_");
    }

    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    EvaluationContext context = new StandardEvaluationContext();

    Object[] args = joinPoint.getArgs();
    String[] paramNames = signature.getParameterNames();

    for (int i = 0; i < args.length; i++) {
      context.setVariable(paramNames[i], args[i]);
    }

    return parser.parseExpression(spelKey).getValue(context, String.class);
  }
}
