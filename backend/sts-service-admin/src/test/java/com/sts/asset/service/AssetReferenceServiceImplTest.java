package com.sts.asset.service;

import com.sts.asset.entity.Asset;
import com.sts.asset.mapper.AssetMapper;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssetReferenceServiceImplTest {

  private AssetMapper assetMapper;
  private AssetReferenceServiceImpl service;

  @BeforeEach
  void setUp() {
    assetMapper = mock(AssetMapper.class);
    service = new AssetReferenceServiceImpl(assetMapper);
  }

  @Test
  @DisplayName("SUCCEEDED 素材可引用")
  void requireUsableAsset_succeeded_ok() {
    Asset asset = asset(1L, "SUCCEEDED");
    when(assetMapper.selectOne(any())).thenReturn(asset);

    Asset result = service.requireUsableAsset(100L);

    assertThat(result).isSameAs(asset);
  }

  @Test
  @DisplayName("PROCESSING 素材 → 409 / 6012")
  void requireUsableAsset_processing_throws6012() {
    when(assetMapper.selectOne(any())).thenReturn(asset(1L, "PROCESSING"));

    assertThatThrownBy(() -> service.requireUsableAsset(100L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(6012));
  }

  @Test
  @DisplayName("素材不存在 → 404 / 6011")
  void requireUsableAsset_notFound_throws6011() {
    when(assetMapper.selectOne(any())).thenReturn(null);

    assertThatThrownBy(() -> service.requireUsableAsset(100L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(6011));
  }

  private static Asset asset(Long orgId, String processStatus) {
    Asset asset = new Asset();
    asset.setOrganizationId(orgId);
    asset.setProcessStatus(processStatus);
    return asset;
  }
}
