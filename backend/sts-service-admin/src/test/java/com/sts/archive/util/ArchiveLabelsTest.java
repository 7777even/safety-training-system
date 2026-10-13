package com.sts.archive.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 档案成品中文口径：已知编码必须译成中文，未知编码原样保留，空值用占位符。
 */
class ArchiveLabelsTest {

  @Test
  void shouldTranslateKnownCodes() {
    assertThat(ArchiveLabels.planStatus("ENDED")).isEqualTo("已结束");
    assertThat(ArchiveLabels.trainingNature("INITIAL")).isEqualTo("初培");
    assertThat(ArchiveLabels.contentType("QUESTION_BANK")).isEqualTo("题库练习");
    assertThat(ArchiveLabels.resultStatus("PASSED")).isEqualTo("合格");
    assertThat(ArchiveLabels.resultStatus("INCOMPLETE")).isEqualTo("结果待补全");
    assertThat(ArchiveLabels.rewardRecordType("PENALTY")).isEqualTo("处罚");
    assertThat(ArchiveLabels.attachmentType("REWARD_PENALTY")).isEqualTo("奖惩佐证");
    assertThat(ArchiveLabels.certificateStatus("ISSUED")).isEqualTo("已签发");
  }

  @Test
  void shouldKeepUnknownCodeVerbatim() {
    assertThat(ArchiveLabels.contentType("SOMETHING_NEW")).isEqualTo("SOMETHING_NEW");
  }

  @Test
  void shouldReturnPlaceholderForNull() {
    assertThat(ArchiveLabels.planStatus(null)).isEqualTo("—");
  }
}
