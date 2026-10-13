package com.sts.asset.service;

public class UnsatisfiableRangeException extends IllegalArgumentException {
  private final long size;

  public UnsatisfiableRangeException(long size) {
    super("invalid range");
    this.size = size;
  }

  public long size() {
    return size;
  }
}
