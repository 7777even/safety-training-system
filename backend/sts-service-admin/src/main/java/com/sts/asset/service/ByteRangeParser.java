package com.sts.asset.service;

public final class ByteRangeParser {
  private ByteRangeParser() {
  }

  public static ByteRange parse(String header, long size) {
    if (size <= 0 || header == null || !header.startsWith("bytes=") || header.contains(",")) {
      throw new UnsatisfiableRangeException(size);
    }
    String value = header.substring(6).trim();
    int dash = value.indexOf('-');
    if (dash < 0 || dash != value.lastIndexOf('-')) throw new UnsatisfiableRangeException(size);
    try {
      if (dash == 0) {
        long suffix = Long.parseLong(value.substring(1));
        if (suffix <= 0) throw new UnsatisfiableRangeException(size);
        suffix = Math.min(suffix, size);
        return new ByteRange(size - suffix, size - 1);
      }
      long start = Long.parseLong(value.substring(0, dash));
      long end = dash == value.length() - 1 ? size - 1 : Long.parseLong(value.substring(dash + 1));
      if (start < 0 || start >= size || end < start) throw new UnsatisfiableRangeException(size);
      return new ByteRange(start, Math.min(end, size - 1));
    } catch (NumberFormatException e) {
      throw new UnsatisfiableRangeException(size);
    }
  }
}
