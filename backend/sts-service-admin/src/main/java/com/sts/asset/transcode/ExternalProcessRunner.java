package com.sts.asset.transcode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ExternalProcessRunner {
  public ProcessResult run(List<String> command, Duration timeout) {
    if (command == null || command.isEmpty()) {
      throw new IllegalArgumentException("command must not be empty");
    }
    Process process;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(true).start();
    } catch (IOException e) {
      throw new TranscodeFailureException("TRANSCODE_ERROR", "无法启动转码程序");
    }
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<byte[]> outputFuture = executor.submit(() -> process.getInputStream().readAllBytes());
      boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        throw new TranscodeFailureException("TIMEOUT", "转码程序执行超时");
      }
      String output = new String(outputFuture.get(), StandardCharsets.UTF_8);
      return new ProcessResult(process.exitValue(), output);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      throw new TranscodeFailureException("TRANSCODE_ERROR", "转码程序执行被中断");
    } catch (ExecutionException e) {
      throw new TranscodeFailureException("TRANSCODE_ERROR", "读取转码程序输出失败");
    }
  }
}
