package com.sts.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.asset.converter.AssetConverter;
import com.sts.asset.dto.AssetCreateRequest;
import com.sts.asset.dto.AssetListQuery;
import com.sts.asset.dto.AssetUpdateRequest;
import com.sts.asset.entity.Asset;
import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetMapper;
import com.sts.asset.mapper.AssetTranscodeJobMapper;
import com.sts.asset.vo.AssetListItemVO;
import com.sts.asset.vo.AssetTranscodeJobListItemVO;
import com.sts.asset.vo.AssetVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.course.service.CourseAssetReferenceService;
import com.sts.course.vo.CourseAssetReferenceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AssetServiceImpl extends BaseServiceImpl<AssetMapper, Asset> implements AssetService {

  private static final String SOURCE_UPLOAD = "UPLOAD";
  private static final String SOURCE_EXTERNAL = "EXTERNAL";
  private static final String SOURCE_CREATED = "CREATED";

  private static final String PROCESS_UPLOADED = "UPLOADED";
  private static final String PROCESS_PROCESSING = "PROCESSING";
  private static final String PROCESS_SUCCEEDED = "SUCCEEDED";
  private static final String PROCESS_FAILED = "FAILED";

  private static final String JOB_PENDING = "PENDING";
  private static final String JOB_FAILED = "FAILED";
  private static final Set<String> JOB_STATUSES = Set.of("PENDING", "RUNNING", "SUCCEEDED", "FAILED");
  private static final String JOB_TYPE_AUDIO = "AUDIO_TRANSCODE";
  private static final String JOB_TYPE_VIDEO = "VIDEO_TRANSCODE";
  private static final String JOB_TYPE_DOCUMENT = "DOCUMENT_PREVIEW";

  private static final Set<String> SYNC_UPLOAD_TYPES = Set.of(
    "IMAGE", "RICH_TEXT", "LINK", "LIVE");
  private static final Set<String> TRANSCODE_TYPES = Set.of("AUDIO", "VIDEO", "DOCUMENT");
  private static final Set<String> DOCUMENT_PREVIEW_SUFFIXES = Set.of("pdf", "doc", "docx", "ppt", "pptx");

  private final AssetMapper assetMapper;
  private final AssetTranscodeJobMapper transcodeJobMapper;
  private final AssetConverter assetConverter;
  private final SysFileInfoMapper sysFileInfoMapper;
  private final CourseAssetReferenceService courseAssetReferenceService;
  private final AssetOriginalFileSizeValidator originalFileSizeValidator;

  @Override
  public PageResult<AssetListItemVO> pageAssets(PageRequest<AssetListQuery> request) {
    AssetListQuery query = request.getQuery();
    // 租户条件由 TenantLineInnerInterceptor 自动注入，业务代码不再手写
    LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.like(StringUtils.hasText(query.getAssetName()), Asset::getAssetName, query.getAssetName())
        .eq(StringUtils.hasText(query.getAssetType()), Asset::getAssetType, query.getAssetType())
        .eq(StringUtils.hasText(query.getProcessStatus()), Asset::getProcessStatus, query.getProcessStatus())
        .eq(query.getStatus() != null, Asset::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(Asset::getCreateTime);
    Page<Asset> page = assetMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    List<AssetListItemVO> list = page.getRecords().stream()
      .map(assetConverter::toListItem)
      .toList();
    return new PageResult<>(list, page.getTotal());
  }

  @Override
  public AssetVO getAsset(Long assetId) {
    Asset asset = requireAsset(assetId);
    return assetConverter.toVO(asset);
  }

  @Override
  @Transactional
  public Long createAsset(AssetCreateRequest request) {
    validateCreateRequest(request);

    Asset asset = assetConverter.toEntity(request);
    applySourceFields(asset, request);

    try {
      assetMapper.insert(asset);
    } catch (DuplicateKeyException e) {
      throw new BusinessException(HttpStatus.CONFLICT, 6021, "素材编码已存在");
    }

    finalizeProcessStatus(asset, request);
    return asset.getAssetId();
  }

  @Override
  @Transactional
  public void updateAsset(Long assetId, AssetUpdateRequest request) {
    Asset asset = requireAsset(assetId);

    if (PROCESS_PROCESSING.equals(asset.getProcessStatus())) {
      if (!request.getStatus().equals(asset.getStatus())) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 6022, "处理中素材仅可修改名称");
      }
      asset.setVersion(request.getVersion());
      asset.setAssetName(request.getAssetName());
    } else {
      asset.setVersion(request.getVersion());
      assetConverter.updateEntity(asset, request);
    }
    assetMapper.updateByIdWithVersionCheck(asset);
  }

  @Override
  @Transactional
  public void deleteAsset(Long assetId) {
    Asset asset = requireAsset(assetId);

    if (PROCESS_PROCESSING.equals(asset.getProcessStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 6022, "处理中素材不可删除");
    }

    CourseAssetReferenceVO references = courseAssetReferenceService.getReferences(assetId);
    if (references.getReferenceCount() != null && references.getReferenceCount() > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 6023, "素材仍被课程引用，不可删除");
    }

    this.removeById(assetId);
  }

  @Override
  public List<AssetTranscodeJobListItemVO> listTranscodeJobs(Long assetId, String jobStatus) {
    if (StringUtils.hasText(jobStatus) && !JOB_STATUSES.contains(jobStatus)) {
      throw validationError("不支持的转码任务状态: " + jobStatus);
    }
    LambdaQueryWrapper<AssetTranscodeJob> wrapper = new LambdaQueryWrapper<AssetTranscodeJob>()
      .eq(assetId != null, AssetTranscodeJob::getAssetId, assetId)
      .eq(StringUtils.hasText(jobStatus), AssetTranscodeJob::getJobStatus, jobStatus)
      .orderByDesc(AssetTranscodeJob::getCreateTime);
    return transcodeJobMapper.selectList(wrapper).stream()
      .map(this::toTranscodeJobListItem)
      .toList();
  }

  @Override
  @Transactional
  public void retryTranscode(Long assetId) {
    Asset asset = requireAsset(assetId);

    if (!PROCESS_FAILED.equals(asset.getProcessStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 6022, "仅失败状态的可转码素材可重试");
    }
    if (!TRANSCODE_TYPES.contains(asset.getAssetType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 6022, "仅音视频或可转换文档支持转码重试");
    }

    AssetTranscodeJob job = transcodeJobMapper.selectOne(
      new LambdaQueryWrapper<AssetTranscodeJob>()
        .eq(AssetTranscodeJob::getAssetId, assetId)
        .eq(AssetTranscodeJob::getJobStatus, JOB_FAILED)
        .orderByDesc(AssetTranscodeJob::getCreateTime)
        .last("LIMIT 1"));
    if (job == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 6024, "转码任务不存在");
    }

    job.setJobStatus(JOB_PENDING);
    job.setFailureCode(null);
    job.setFailureReason(null);
    job.setStartedTime(null);
    job.setFinishedTime(null);
    job.setRetryCount(job.getRetryCount() != null ? job.getRetryCount() + 1 : 1);
    transcodeJobMapper.updateById(job);

    asset.setProcessStatus(PROCESS_UPLOADED);
    assetMapper.updateById(asset);

  }

  private void validateCreateRequest(AssetCreateRequest request) {
    String sourceType = request.getSourceType();
    switch (sourceType) {
      case SOURCE_UPLOAD -> {
        if (request.getFileInfoId() == null) {
          throw validationError("UPLOAD 来源须指定 fileInfoId");
        }
        SysFileInfo fileInfo = sysFileInfoMapper.selectById(request.getFileInfoId());
        if (fileInfo == null) {
          throw validationError("上传文件不存在");
        }
        originalFileSizeValidator.validateByType(
          request.getAssetType(), fileInfo.getFileSize(), AssetOriginalFileSizeValidator.CREATE_ERROR_CODE);
      }
      case SOURCE_EXTERNAL -> {
        if (!StringUtils.hasText(request.getExternalUrl())) {
          throw validationError("EXTERNAL 来源须指定 externalUrl");
        }
        if (!isHttpUrl(request.getExternalUrl())) {
          throw validationError("外部链接须为 http 或 https");
        }
      }
      case SOURCE_CREATED -> {
        if (!StringUtils.hasText(request.getRichContent())) {
          throw validationError("CREATED 来源须指定 richContent");
        }
      }
      default -> throw validationError("不支持的来源类型: " + sourceType);
    }
  }

  private void applySourceFields(Asset asset, AssetCreateRequest request) {
    String sourceType = request.getSourceType();
    switch (sourceType) {
      case SOURCE_UPLOAD -> {
        asset.setFileInfoId(request.getFileInfoId());
        asset.setExternalUrl(null);
        asset.setRichContent(null);
        asset.setProcessStatus(PROCESS_UPLOADED);
      }
      case SOURCE_EXTERNAL -> {
        asset.setFileInfoId(null);
        asset.setExternalUrl(request.getExternalUrl());
        asset.setRichContent(null);
        asset.setProcessStatus(PROCESS_SUCCEEDED);
      }
      case SOURCE_CREATED -> {
        asset.setFileInfoId(null);
        asset.setExternalUrl(null);
        asset.setRichContent(request.getRichContent());
        asset.setProcessStatus(PROCESS_SUCCEEDED);
      }
      default -> throw validationError("不支持的来源类型: " + sourceType);
    }
  }

  private void finalizeProcessStatus(Asset asset, AssetCreateRequest request) {
    if (!SOURCE_UPLOAD.equals(request.getSourceType())) {
      return;
    }

    String assetType = request.getAssetType();
    if ("DOCUMENT".equals(assetType)) {
      SysFileInfo fileInfo = sysFileInfoMapper.selectById(request.getFileInfoId());
      String suffix = fileInfo == null ? null : fileInfo.getFileSuffix();
      if (suffix == null || !DOCUMENT_PREVIEW_SUFFIXES.contains(suffix.toLowerCase())) {
        asset.setProcessStatus(PROCESS_SUCCEEDED);
        assetMapper.updateById(asset);
        return;
      }
      createTranscodeJob(asset, JOB_TYPE_DOCUMENT);
      return;
    }
    if (SYNC_UPLOAD_TYPES.contains(assetType)) {
      asset.setProcessStatus(PROCESS_SUCCEEDED);
      assetMapper.updateById(asset);
      return;
    }

    if (TRANSCODE_TYPES.contains(assetType)) {
      createTranscodeJob(asset, "VIDEO".equals(assetType) ? JOB_TYPE_VIDEO : JOB_TYPE_AUDIO);
    }
  }

  private void createTranscodeJob(Asset asset, String jobType) {
    AssetTranscodeJob job = new AssetTranscodeJob();
    job.setAssetId(asset.getAssetId());
    job.setJobType(jobType);
    job.setJobStatus(JOB_PENDING);
    job.setRetryCount(0);
    transcodeJobMapper.insert(job);
  }

  private Asset requireAsset(Long assetId) {
    Asset asset = assetMapper.selectOne(
      new LambdaQueryWrapper<Asset>()
        .eq(Asset::getAssetId, assetId)
    );
    if (asset == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 6011, "素材不存在");
    }
    return asset;
  }

  private AssetTranscodeJobListItemVO toTranscodeJobListItem(AssetTranscodeJob job) {
    AssetTranscodeJobListItemVO vo = new AssetTranscodeJobListItemVO();
    vo.setTranscodeJobId(job.getTranscodeJobId());
    vo.setAssetId(job.getAssetId());
    vo.setJobType(job.getJobType());
    vo.setJobStatus(job.getJobStatus());
    vo.setStartedTime(job.getStartedTime());
    vo.setFinishedTime(job.getFinishedTime());
    vo.setRetryCount(job.getRetryCount());
    vo.setFailureCode(job.getFailureCode());
    vo.setFailureReason(job.getFailureReason());
    vo.setCreateTime(job.getCreateTime());
    return vo;
  }

  private static boolean isHttpUrl(String url) {
    return url.startsWith("http://") || url.startsWith("https://");
  }

  private static BusinessException validationError(String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, 6021, message);
  }
}
