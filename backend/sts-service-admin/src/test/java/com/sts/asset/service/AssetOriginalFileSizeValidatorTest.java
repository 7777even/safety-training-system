package com.sts.asset.service;

import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AssetOriginalFileSizeValidatorTest {

  private static final long TWO_GB = 2L * 1024 * 1024 * 1024;
  private static final long FIVE_HUNDRED_TWELVE_MB = 512L * 1024 * 1024;
  private static final long SIXTEEN_MB = 16L * 1024 * 1024;

  private final AssetOriginalFileSizeValidator validator = new AssetOriginalFileSizeValidator();

  @Test
  @DisplayName("音视频等于 2GB 通过，超过则 400/5007")
  void audioVideoAtLimitPassesAndOverLimitRejected() {
    assertThatCode(() -> validator.validateByType("VIDEO", TWO_GB, 5007)).doesNotThrowAnyException();
    assertThatCode(() -> validator.validateByType("AUDIO", TWO_GB, 5007)).doesNotThrowAnyException();

    assertThatThrownBy(() -> validator.validateByType("VIDEO", TWO_GB + 1, 5007))
      .isInstanceOfSatisfying(BusinessException.class, error -> {
        assertThat(error.getCode()).isEqualTo(5007);
        assertThat(error.getMessage()).isEqualTo("音视频原始文件不能超过 2GB");
      });
  }

  @Test
  @DisplayName("文档等于 512MB 通过，超过则拒绝")
  void documentAtLimitPassesAndOverLimitRejected() {
    assertThatCode(() -> validator.validateByType("DOCUMENT", FIVE_HUNDRED_TWELVE_MB, 6025))
      .doesNotThrowAnyException();

    assertThatThrownBy(() -> validator.validateByType("DOCUMENT", FIVE_HUNDRED_TWELVE_MB + 1, 6025))
      .isInstanceOfSatisfying(BusinessException.class, error -> {
        assertThat(error.getCode()).isEqualTo(6025);
        assertThat(error.getMessage()).isEqualTo("文档原始文件不能超过 512MB");
      });
  }

  @Test
  @DisplayName("图片等于 16MB 通过，超过则拒绝")
  void imageAtLimitPassesAndOverLimitRejected() {
    assertThatCode(() -> validator.validateByType("IMAGE", SIXTEEN_MB, 5007)).doesNotThrowAnyException();

    assertThatThrownBy(() -> validator.validateByType("IMAGE", SIXTEEN_MB + 1, 5007))
      .isInstanceOfSatisfying(BusinessException.class, error -> {
        assertThat(error.getCode()).isEqualTo(5007);
        assertThat(error.getMessage()).isEqualTo("图片原始文件不能超过 16MB");
      });
  }

  @Test
  @DisplayName("非素材目录不按原始文件类型上限拦截")
  void nonAssetFolderIsIgnored() {
    assertThatCode(() -> validator.validateAssetFolderUpload(
      "general", "cover.png", "image/png", SIXTEEN_MB + 1)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("素材目录按扩展名推断类型后再校验")
  void assetFolderInfersTypeFromFileName() {
    assertThatThrownBy(() -> validator.validateAssetFolderUpload(
      "asset", "safety.png", "image/png", SIXTEEN_MB + 1))
      .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(5007));

    assertThatThrownBy(() -> validator.validateAssetFolderUpload(
      "asset", "manual.pdf", "application/pdf", FIVE_HUNDRED_TWELVE_MB + 1))
      .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(5007));

    assertThatThrownBy(() -> validator.validateAssetFolderUpload(
      "asset", "lesson.mp4", "video/mp4", TWO_GB + 1))
      .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(5007));
  }
}
