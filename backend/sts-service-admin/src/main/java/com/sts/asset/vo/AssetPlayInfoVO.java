package com.sts.asset.vo;

public record AssetPlayInfoVO(
  Long assetId, String format, String url, String coverUrl,
  Integer durationSeconds, Integer pageCount) {
}
