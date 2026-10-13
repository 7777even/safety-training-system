package com.sts.asset.transcode;

import lombok.Getter;

@Getter
public class TranscodeFailureException extends RuntimeException {
  private final String failureCode;

  public TranscodeFailureException(String failureCode, String message) {
    super(message);
    this.failureCode = failureCode;
  }

  public TranscodeFailureException(String failureCode, String message, Throwable cause) {
    super(message, cause);
    this.failureCode = failureCode;
  }
}
