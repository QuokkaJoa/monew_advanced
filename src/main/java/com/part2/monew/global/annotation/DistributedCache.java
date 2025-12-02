package com.part2.monew.global.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedCache {
  String cacheName();
  String key() default "";
  long waitTime() default 5;
  long leaseTime() default 10;
  TimeUnit timeUnit() default TimeUnit.SECONDS;
}
