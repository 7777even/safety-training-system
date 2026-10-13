package com.sts.asset.service;

import com.sts.asset.dto.AssetCreateRequest;
import com.sts.asset.dto.AssetListQuery;
import com.sts.asset.dto.AssetUpdateRequest;
import com.sts.asset.vo.AssetListItemVO;
import com.sts.asset.vo.AssetTranscodeJobListItemVO;
import com.sts.asset.vo.AssetVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;

import java.util.List;

public interface AssetService {

  PageResult<AssetListItemVO> pageAssets(PageRequest<AssetListQuery> request);

  AssetVO getAsset(Long assetId);

  Long createAsset(AssetCreateRequest request);

  void updateAsset(Long assetId, AssetUpdateRequest request);

  void deleteAsset(Long assetId);

  List<AssetTranscodeJobListItemVO> listTranscodeJobs(Long assetId, String jobStatus);

  void retryTranscode(Long assetId);
}
