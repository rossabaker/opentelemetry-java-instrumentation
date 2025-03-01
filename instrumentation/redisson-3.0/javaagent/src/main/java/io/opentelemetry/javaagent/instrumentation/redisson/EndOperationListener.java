/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import static io.opentelemetry.javaagent.instrumentation.redisson.RedissonSingletons.instrumenter;

import io.opentelemetry.context.Context;
import java.util.function.BiConsumer;

public final class EndOperationListener<T> implements BiConsumer<T, Throwable> {
  private final Context context;
  private final RedissonRequest request;

  public EndOperationListener(Context context, RedissonRequest request) {
    this.context = context;
    this.request = request;
  }

  @Override
  public void accept(T t, Throwable error) {
    instrumenter().end(context, request, null, error);
  }
}
