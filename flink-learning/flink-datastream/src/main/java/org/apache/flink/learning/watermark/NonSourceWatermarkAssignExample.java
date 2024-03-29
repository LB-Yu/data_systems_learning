package org.apache.flink.learning.watermark;

import cn.hutool.core.date.LocalDateTimeUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.time.LocalDateTime;

public class NonSourceWatermarkAssignExample {

  public static void main(String[] args) throws Exception {
    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    env.getConfig().setAutoWatermarkInterval(1);

    KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers("localhost:9092")
            .setTopics("test1")
            .setGroupId("my-group")
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
    DataStream<String> streamSource = env.fromSource(
            source,
            WatermarkStrategy.noWatermarks(),
            "kafka-source");

    DataStream<Tuple3<Integer, String, Long>> tupleStream = streamSource
            .map(new MapFunction<String, Tuple3<Integer, String, Long>>() {
      @Override
      public Tuple3<Integer, String, Long> map(String s) throws Exception {
        String[] items = s.split(",");
        int id = Integer.parseInt(items[0]);
        String value = items[1];
        LocalDateTime localDateTime = LocalDateTimeUtil.parse(items[2], "yyyy-MM-dd HH:mm:ss");
        long timestamp = LocalDateTimeUtil.toEpochMilli(localDateTime);
        System.out.println("map output: " + new Tuple3<>(id, value, timestamp));
        return new Tuple3<>(id, value, timestamp);
      }
    });

    DataStream<Tuple3<Integer, String, Long>> watermarks = tupleStream
            .assignTimestampsAndWatermarks(WatermarkStrategy
            .<Tuple3<Integer, String, Long>>forBoundedOutOfOrderness(Duration.ofSeconds(5))
            .withTimestampAssigner((t, time) -> t.f2));

    watermarks
            .keyBy(t -> t.f0)
            .window(TumblingEventTimeWindows.of(Time.seconds(10)))
            .apply(new WindowFunction<Tuple3<Integer, String, Long>, Integer, Integer, TimeWindow>() {

              @Override
              public void apply(Integer key,
                                TimeWindow timeWindow,
                                Iterable<Tuple3<Integer, String, Long>> iterable,
                                Collector<Integer> collector) throws Exception {
                System.out.println("========");
                System.out.println("Window key: " + key);
                System.out.println("Window range: ["
                        + LocalDateTimeUtil.format(LocalDateTimeUtil.of(timeWindow.getStart()), "yyyy-MM-dd HH:mm:ss")
                        + ", "
                        + LocalDateTimeUtil.format(LocalDateTimeUtil.of(timeWindow.getEnd()), "yyyy-MM-dd HH:mm:ss")
                        + "]");
                System.out.println("Window items:");
                int count = 0;
                for (Tuple3<Integer, String, Long> t : iterable) {
                  System.out.println(t);
                  ++count;
                }
                collector.collect(count);
                System.out.println("========");
              }
            });

    env.execute();
  }
}
