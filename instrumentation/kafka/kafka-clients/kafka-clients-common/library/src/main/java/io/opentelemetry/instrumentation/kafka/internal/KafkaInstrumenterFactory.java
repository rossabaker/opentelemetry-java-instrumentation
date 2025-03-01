/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.ExperimentalConfig;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import java.util.Collections;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaInstrumenterFactory {

  public static Instrumenter<ProducerRecord<?, ?>, Void> createProducerInstrumenter(
      String instrumentationName) {
    return createProducerInstrumenter(
        instrumentationName, GlobalOpenTelemetry.get(), Collections.emptyList());
  }

  public static Instrumenter<ProducerRecord<?, ?>, Void> createProducerInstrumenter(
      String instrumentationName,
      OpenTelemetry openTelemetry,
      Iterable<AttributesExtractor<ProducerRecord<?, ?>, Void>> extractors) {

    KafkaProducerAttributesGetter getter = KafkaProducerAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.SEND;

    return Instrumenter.<ProducerRecord<?, ?>, Void>builder(
            openTelemetry,
            instrumentationName,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(MessagingAttributesExtractor.create(getter, operation))
        .addAttributesExtractors(extractors)
        .addAttributesExtractor(new KafkaProducerAdditionalAttributesExtractor())
        .newInstrumenter(SpanKindExtractor.alwaysProducer());
  }

  public static Instrumenter<ReceivedRecords, Void> createConsumerReceiveInstrumenter(
      String instrumentationName) {
    return createConsumerReceiveInstrumenter(
        instrumentationName, GlobalOpenTelemetry.get(), Collections.emptyList());
  }

  public static Instrumenter<ReceivedRecords, Void> createConsumerReceiveInstrumenter(
      String instrumentationName,
      OpenTelemetry openTelemetry,
      Iterable<AttributesExtractor<ReceivedRecords, Void>> extractors) {

    KafkaReceiveAttributesGetter getter = KafkaReceiveAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.RECEIVE;

    return Instrumenter.<ReceivedRecords, Void>builder(
            openTelemetry,
            instrumentationName,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(MessagingAttributesExtractor.create(getter, operation))
        .addAttributesExtractors(extractors)
        .setTimeExtractor(new KafkaConsumerTimeExtractor())
        .setEnabled(ExperimentalConfig.get().messagingReceiveInstrumentationEnabled())
        .newInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public static Instrumenter<ConsumerRecord<?, ?>, Void> createConsumerProcessInstrumenter(
      String instrumentationName) {
    return createConsumerOperationInstrumenter(
        instrumentationName,
        GlobalOpenTelemetry.get(),
        MessageOperation.PROCESS,
        Collections.emptyList());
  }

  public static Instrumenter<ConsumerRecord<?, ?>, Void> createConsumerOperationInstrumenter(
      String instrumentationName,
      OpenTelemetry openTelemetry,
      MessageOperation operation,
      Iterable<AttributesExtractor<ConsumerRecord<?, ?>, Void>> extractors) {

    KafkaConsumerAttributesGetter getter = KafkaConsumerAttributesGetter.INSTANCE;

    InstrumenterBuilder<ConsumerRecord<?, ?>, Void> builder =
        Instrumenter.<ConsumerRecord<?, ?>, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operation))
            .addAttributesExtractor(MessagingAttributesExtractor.create(getter, operation))
            .addAttributesExtractor(new KafkaConsumerAdditionalAttributesExtractor())
            .addAttributesExtractors(extractors);
    if (KafkaConsumerExperimentalAttributesExtractor.isEnabled()) {
      builder.addAttributesExtractor(new KafkaConsumerExperimentalAttributesExtractor());
    }

    if (!KafkaPropagation.isPropagationEnabled()) {
      return builder.newInstrumenter(SpanKindExtractor.alwaysConsumer());
    } else if (ExperimentalConfig.get().messagingReceiveInstrumentationEnabled()) {
      builder.addSpanLinksExtractor(
          SpanLinksExtractor.fromUpstreamRequest(
              GlobalOpenTelemetry.getPropagators(), KafkaConsumerRecordGetter.INSTANCE));
      return builder.newInstrumenter(SpanKindExtractor.alwaysConsumer());
    } else {
      return builder.newConsumerInstrumenter(KafkaConsumerRecordGetter.INSTANCE);
    }
  }

  private KafkaInstrumenterFactory() {}
}
