package com.sts.asset.service;

public record ByteRange(long start, long end) {
  public long length() {
    return end - start + 1;
  }
}
