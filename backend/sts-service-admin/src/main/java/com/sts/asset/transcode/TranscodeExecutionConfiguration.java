package com.sts.asset.transcode;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TranscodeExecutionConfiguration {
  @Bean("transcodeTaskExecutor")
  public TaskExecutor transcodeTaskExecutor(TranscodeProperties properties) {
    int concurrency = properties.getConcurrency();
    if (concurrency < 1) throw new IllegalStateException("sts.transcode.concurrency must be positive");
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(concurrency);
    executor.setMaxPoolSize(concurrency);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("transcode-");
    executor.initialize();
    return executor;
  }
}
