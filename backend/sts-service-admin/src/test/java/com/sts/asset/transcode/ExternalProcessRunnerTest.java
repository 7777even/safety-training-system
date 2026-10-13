package com.sts.asset.transcode;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalProcessRunnerTest {
  private final ExternalProcessRunner runner = new ExternalProcessRunner();

  @Test
  void capturesOutputWithoutShell() {
    ProcessResult result = runner.run(List.of("/bin/sh", "-c", "printf safe"), Duration.ofSeconds(2));
    assertThat(result.exitCode()).isZero();
    assertThat(result.output()).isEqualTo("safe");
  }

  @Test
  void timeoutTerminatesProcess() {
    assertThatThrownBy(() -> runner.run(
      List.of("/bin/sh", "-c", "sleep 5"), Duration.ofMillis(50)))
      .isInstanceOf(TranscodeFailureException.class)
      .extracting("failureCode").isEqualTo("TIMEOUT");
  }
}
