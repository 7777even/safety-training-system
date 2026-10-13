package com.sts.asset.transcode;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sts.transcode")
public class TranscodeProperties {
  private String provider = "stub";
  private String documentProvider = "disabled";
  private int concurrency = 2;
  private int runningTimeoutMinutes = 30;
  private long pollIntervalMs = 3_000;
  private long timeoutScanIntervalMs = 60_000;
  private String ffmpegPath = "/usr/bin/ffmpeg";
  private String ffprobePath = "/usr/bin/ffprobe";
  private String qpdfPath = "/usr/bin/qpdf";
  private String workDir = "/tmp/sts-transcode";
  private int processTimeoutMinutes = 20;
  private int pdfLinearizeTimeoutSeconds = 120;
}
