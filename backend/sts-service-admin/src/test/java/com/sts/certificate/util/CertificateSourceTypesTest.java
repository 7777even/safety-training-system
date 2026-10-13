package com.sts.certificate.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 证书来源类型中文口径：证书是给学员看的成品，不得出现库内英文编码。
 */
class CertificateSourceTypesTest {

  @Test
  @DisplayName("三种来源类型都译为中文，且与签发规则页口径一致")
  void shouldTranslateEverySupportedCode() {
    assertThat(CertificateSourceTypes.label("COURSE")).isEqualTo("课程");
    assertThat(CertificateSourceTypes.label("EXAM")).isEqualTo("理论考试");
    assertThat(CertificateSourceTypes.label("TRAINING")).isEqualTo("培训计划");
    assertThat(CertificateSourceTypes.codes()).containsExactlyInAnyOrder("COURSE", "EXAM", "TRAINING");
  }

  @Test
  @DisplayName("未知编码原样返回，来源脏数据要看得见而不是被吞掉")
  void shouldKeepUnknownCodeAsIs() {
    assertThat(CertificateSourceTypes.label("OFFLINE")).isEqualTo("OFFLINE");
    assertThat(CertificateSourceTypes.isSupported("OFFLINE")).isFalse();
    assertThat(CertificateSourceTypes.isSupported("TRAINING")).isTrue();
    assertThat(CertificateSourceTypes.isSupported(null)).isFalse();
  }

  @Test
  @DisplayName("来源类型缺失时渲染空串，证书上不出现 null")
  void shouldRenderEmptyForMissingCode() {
    assertThat(CertificateSourceTypes.label(null)).isEmpty();
  }
}
