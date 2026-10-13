package com.sts.asset.service;

import com.sts.asset.entity.Asset;

public interface AssetReferenceService {

  /**
   * 要求素材存在、未删除且 processStatus=SUCCEEDED。不存在抛 6011，不可用抛 6012。
   * <p>
   * 租户边界由租户插件保证：跨租户的素材 ID 在本租户上下文下查不到，等同于"不存在"。
   */
  Asset requireUsableAsset(Long assetId);
}
