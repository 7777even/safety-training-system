package com.sts.asset.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.asset.dto.AssetCreateRequest;
import com.sts.asset.dto.AssetListQuery;
import com.sts.asset.dto.AssetUpdateRequest;
import com.sts.asset.service.AssetService;
import com.sts.asset.vo.AssetListItemVO;
import com.sts.asset.vo.AssetTranscodeJobListItemVO;
import com.sts.asset.vo.AssetVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 素材元数据、文件关联及转码任务管理接口。
 */
@RestController
@RequiredArgsConstructor
public class AssetController {

  private final AssetService assetService;

  /**
   * 分页查询素材。
   */
  @GetMapping("/asset/assets")
  @SaCheckPermission("asset:asset:list")
  public PageResult<AssetListItemVO> pageAssets(PageRequest<AssetListQuery> request) {
    return assetService.pageAssets(request);
  }

  /**
   * 查询素材。
   */
  @GetMapping("/asset/assets/{assetId}")
  @SaCheckPermission("asset:asset:list")
  public AssetVO getAsset(@PathVariable Long assetId) {
    return assetService.getAsset(assetId);
  }

  /**
   * 创建素材。
   */
  @PostMapping("/asset/assets")
  @SaCheckPermission("asset:asset:add")
  public Long createAsset(@RequestBody @Valid AssetCreateRequest request) {
    return assetService.createAsset(request);
  }

  /**
   * 更新素材。
   */
  @PutMapping("/asset/assets/{assetId}")
  @SaCheckPermission("asset:asset:edit")
  public void updateAsset(@PathVariable Long assetId,
                          @RequestBody @Valid AssetUpdateRequest request) {
    assetService.updateAsset(assetId, request);
  }

  /**
   * 删除素材。
   */
  @DeleteMapping("/asset/assets/{assetId}")
  @SaCheckPermission("asset:asset:delete")
  public void deleteAsset(@PathVariable Long assetId) {
    assetService.deleteAsset(assetId);
  }

  /**
   * 查询转码任务列表。
   */
  @GetMapping("/asset/transcode-jobs")
  @SaCheckPermission("asset:job:list")
  public List<AssetTranscodeJobListItemVO> listTranscodeJobs(
    @RequestParam(required = false) Long assetId,
    @RequestParam(required = false) String jobStatus) {
    return assetService.listTranscodeJobs(assetId, jobStatus);
  }

  /**
   * 重试素材。
   */
  @PostMapping("/asset/assets/{assetId}/retry-transcode")
  @SaCheckPermission("asset:asset:edit")
  public void retryTranscode(@PathVariable Long assetId) {
    assetService.retryTranscode(assetId);
  }
}
