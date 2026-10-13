package com.sts.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.asset.entity.Asset;
import com.sts.asset.mapper.AssetMapper;
import com.sts.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssetReferenceServiceImpl implements AssetReferenceService {

  private static final String PROCESS_SUCCEEDED = "SUCCEEDED";

  private final AssetMapper assetMapper;

  @Override
  public Asset requireUsableAsset(Long assetId) {
    Asset asset = assetMapper.selectOne(
      new LambdaQueryWrapper<Asset>().eq(Asset::getAssetId, assetId));
    if (asset == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 6011, "素材不存在");
    }
    if (!PROCESS_SUCCEEDED.equals(asset.getProcessStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 6012, "素材尚未处理完成，暂不可引用");
    }
    return asset;
  }
}
