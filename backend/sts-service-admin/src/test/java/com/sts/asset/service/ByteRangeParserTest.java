package com.sts.asset.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ByteRangeParserTest {
  @Test
  void parsesClosedOpenEndedAndSuffixRanges() {
    assertThat(ByteRangeParser.parse("bytes=2-5", 10)).isEqualTo(new ByteRange(2, 5));
    assertThat(ByteRangeParser.parse("bytes=7-", 10)).isEqualTo(new ByteRange(7, 9));
    assertThat(ByteRangeParser.parse("bytes=-3", 10)).isEqualTo(new ByteRange(7, 9));
  }

  @Test
  void rejectsMultipleMalformedAndUnsatisfiableRanges() {
    assertThatThrownBy(() -> ByteRangeParser.parse("bytes=0-1,3-4", 10))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ByteRangeParser.parse("bytes=10-", 10))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ByteRangeParser.parse("items=0-1", 10))
      .isInstanceOf(IllegalArgumentException.class);
  }
}
