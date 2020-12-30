package org.kestra.runner.kafka.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.Trigger;
import org.kestra.runner.kafka.KafkaExecutor;
import org.kestra.runner.kafka.serializers.JsonSerde;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Slf4j
public class KafkaStreamSourceService {
    public static final String TOPIC_FLOWLAST = "flowlast";
    public static final String TOPIC_EXECUTOR = "executor";
    public static final String TOPIC_EXECUTOR_WORKERINSTANCE = "executorworkerinstance";

    @Inject
    private KafkaAdminService kafkaAdminService;

    public GlobalKTable<String, Flow> flowGlobalKTable(StreamsBuilder builder) {
        return builder
            .globalTable(
                kafkaAdminService.getTopicName(Flow.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Flow.class)),
                Materialized.<String, Flow, KeyValueStore<Bytes, byte[]>>as("flow")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Flow.class))
            );
    }

    public KStream<String, Execution> executorKStream(StreamsBuilder builder) {
        return builder
            .stream(
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class))
            );
    }

    public KTable<String, Execution> executorKTable(StreamsBuilder builder) {
        return builder
            .table(
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class)),
                Materialized.<String, Execution, KeyValueStore<Bytes, byte[]>>as("execution")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Execution.class))
            );
    }

    public GlobalKTable<String, Execution> executorGlobalKTable(StreamsBuilder builder) {
        return builder
            .globalTable(
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class)),
                Materialized.<String, Execution, KeyValueStore<Bytes, byte[]>>as("execution")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Execution.class))
            );
    }

    public GlobalKTable<String, Trigger> triggerGlobalKTable(StreamsBuilder builder) {
        return builder
            .globalTable(
                kafkaAdminService.getTopicName(Trigger.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Trigger.class)),
                Materialized.<String, Trigger, KeyValueStore<Bytes, byte[]>>as("trigger")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Trigger.class))
            );
    }

    public KStream<String, KafkaExecutor.ExecutionWithFlow> withFlow(GlobalKTable<String, Flow> flowGlobalKTable, KStream<String, Execution> executionKStream) {
        return executionKStream
            .filter(
                (key, value) -> value != null, Named.as("withFlow-notNull-filter"))
            .join(
                flowGlobalKTable,
                (key, value) -> Flow.uid(value),
                (execution, flow) -> new KafkaExecutor.ExecutionWithFlow(flow, execution),
                Named.as("withFlow-join")
            );
    }


    public static <T> KStream<String, T> logIfEnabled(KStream<String, T> stream, ForeachAction<String, T> action, String name) {
        if (log.isDebugEnabled()) {
            return stream
                .filter((key, value) -> value != null, Named.as(name + "-null-filter"))
                .peek(action, Named.as(name + "-peek"));
        } else {
            return stream;
        }
    }
}