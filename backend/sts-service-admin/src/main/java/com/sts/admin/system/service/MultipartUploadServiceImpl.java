package com.sts.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.FileMultipartUpload;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.FileMultipartUploadMapper;
import com.sts.admin.storage.*;
import com.sts.admin.system.dto.MultipartCompleteRequest;
import com.sts.admin.system.dto.MultipartInitRequest;
import com.sts.admin.system.vo.MultipartCompleteVO;
import com.sts.admin.system.vo.MultipartInitVO;
import com.sts.admin.system.vo.MultipartPartVO;
import com.sts.admin.tenant.CurrentTenantResolver;
import com.sts.admin.tenant.TenantContext;
import com.sts.asset.service.AssetOriginalFileSizeValidator;
import com.sts.common.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
public class MultipartUploadServiceImpl implements MultipartUploadService {
  private static final String UPLOADING = "UPLOADING";
  private static final String COMPLETED = "COMPLETED";
  private static final String ABORTED = "ABORTED";
  private static final String EXPIRED = "EXPIRED";

  private final FileMultipartUploadMapper sessionMapper;
  private final S3StorageGateway gateway;
  private final CurrentTenantResolver tenantResolver;
  private final TenantObjectKeyService objectKeys;
  private final MultipartUploadPersistenceService persistenceService;
  private final AssetWordDocumentValidator wordDocumentValidator;
  private final AssetOriginalFileSizeValidator originalFileSizeValidator;
  private final long partSize;
  private final int expireHours;

  public MultipartUploadServiceImpl(
    FileMultipartUploadMapper sessionMapper,
    S3StorageGateway gateway,
    CurrentTenantResolver tenantResolver,
    TenantObjectKeyService objectKeys,
    MultipartUploadPersistenceService persistenceService,
    AssetWordDocumentValidator wordDocumentValidator,
    AssetOriginalFileSizeValidator originalFileSizeValidator,
    StorageProperties storageProperties) {
    this.sessionMapper = sessionMapper;
    this.gateway = gateway;
    this.tenantResolver = tenantResolver;
    this.objectKeys = objectKeys;
    this.persistenceService = persistenceService;
    this.wordDocumentValidator = wordDocumentValidator;
    this.originalFileSizeValidator = originalFileSizeValidator;
    this.partSize = storageProperties.getMinio().getMultipartPartSize();
    this.expireHours = storageProperties.getMinio().getMultipartExpireHours();
  }

  @Override
  public MultipartInitVO init(Long userId, MultipartInitRequest request) {
    if ("course-cover".equals(request.getFolder())) {
      throw error(HttpStatus.BAD_REQUEST, 5001, "课程封面必须使用课程封面上传接口");
    }
    originalFileSizeValidator.validateAssetFolderUpload(
      request.getFolder(), request.getFileName(), request.getContentType(), request.getFileSize());
    Long organizationId = tenantResolver.resolve(userId);
    FileMultipartUpload existing = sessionMapper.selectOne(new LambdaQueryWrapper<FileMultipartUpload>()
                                                             .eq(FileMultipartUpload::getOrganizationId, organizationId)
                                                             .eq(FileMultipartUpload::getUserId, userId)
                                                             .eq(FileMultipartUpload::getChecksum,
                                                                 request.getChecksum())
                                                             .eq(FileMultipartUpload::getFileSize,
                                                                 request.getFileSize())
                                                             .eq(FileMultipartUpload::getOriginalFileName,
                                                                 request.getFileName())
                                                             .eq(FileMultipartUpload::getFolder, request.getFolder())
                                                             .eq(FileMultipartUpload::getUploadStatus, UPLOADING)
                                                             .gt(FileMultipartUpload::getExpiresTime,
                                                                 LocalDateTime.now())
                                                             .orderByDesc(FileMultipartUpload::getCreateTime)
                                                             .last("LIMIT 1"));
    if (existing != null) {
      List<Integer> uploaded = gateway.listParts(existing.getObjectName(), existing.getProviderUploadId())
        .stream().map(UploadedStoragePart::partNumber).toList();
      return new MultipartInitVO(existing.getUploadId(), existing.getPartSize(), uploaded);
    }

    String objectName = objectKeys.forCurrentTenant(MinioStorageService.generatedObjectName(request.getFileName()));
    String providerUploadId = gateway.createMultipartUpload(objectName, request.getContentType());
    FileMultipartUpload session = new FileMultipartUpload();
    session.setUploadId(UUID.randomUUID().toString());
    session.setOrganizationId(organizationId);
    session.setUserId(userId);
    session.setProviderUploadId(providerUploadId);
    session.setBucketName(gateway.bucketName());
    session.setObjectName(objectName);
    session.setFolder(request.getFolder());
    session.setOriginalFileName(request.getFileName());
    session.setContentType(request.getContentType());
    session.setFileSize(request.getFileSize());
    session.setChecksum(request.getChecksum());
    session.setPartSize(partSize);
    session.setUploadStatus(UPLOADING);
    session.setExpiresTime(LocalDateTime.now().plusHours(expireHours));
    try {
      sessionMapper.insert(session);
    } catch (RuntimeException e) {
      gateway.abortMultipartUpload(objectName, providerUploadId);
      throw e;
    }
    return new MultipartInitVO(session.getUploadId(), partSize, List.of());
  }

  @Override
  public MultipartPartVO uploadPart(Long userId, String uploadId, Integer partNumber, MultipartFile file) {
    FileMultipartUpload session = requireOwnedActive(userId, uploadId);
    if (file == null || file.isEmpty() || file.getSize() > session.getPartSize()) {
      throw error(HttpStatus.BAD_REQUEST, 5004, "分片为空或超过允许大小");
    }
    long maximumParts = (session.getFileSize() + session.getPartSize() - 1) / session.getPartSize();
    if (partNumber > maximumParts || partNumber > 10_000) {
      throw error(HttpStatus.BAD_REQUEST, 5004, "分片号超出文件范围");
    }
    try {
      String etag = gateway.uploadPart(session.getObjectName(), session.getProviderUploadId(),
                                       partNumber, file.getInputStream(), file.getSize());
      return new MultipartPartVO(partNumber, etag);
    } catch (IOException e) {
      throw new UncheckedIOException("分片读取失败", e);
    }
  }

  @Override
  public MultipartCompleteVO complete(Long userId, String uploadId, MultipartCompleteRequest request) {
    FileMultipartUpload session = requireOwnedActive(userId, uploadId);
    List<UploadedStoragePart> storedParts = gateway.listParts(session.getObjectName(), session.getProviderUploadId());
    validateParts(storedParts, request.parts(), session.getFileSize());
    gateway.completeMultipartUpload(session.getObjectName(), session.getProviderUploadId(), request.parts());
    long actualSize = gateway.objectSize(session.getObjectName());
    String actualChecksum = gateway.sha256(session.getObjectName());
    if (actualSize != session.getFileSize() || !actualChecksum.equals(session.getChecksum())) {
      gateway.deleteObject(session.getObjectName());
      session.setUploadStatus(ABORTED);
      sessionMapper.updateById(session);
      throw error(HttpStatus.BAD_REQUEST, 5005, "文件大小或 checksum 校验失败");
    }
    validateCompletedWordDocument(session);

    SysFileInfo fileInfo = new SysFileInfo();
    fileInfo.setFileName(session.getOriginalFileName());
    fileInfo.setBucketName(session.getFolder());
    fileInfo.setObjectName(session.getObjectName());
    fileInfo.setFileSize(session.getFileSize());
    fileInfo.setFileSuffix(extractSuffix(session.getOriginalFileName()));
    fileInfo.setFileUrl("minio-private://" + session.getObjectName());
    try {
      // 请求上下文已经绑定登录租户，文件记录由租户插件统一填充 organization_id。
      Long fileInfoId = persistenceService.complete(session, fileInfo);
      return new MultipartCompleteVO(fileInfoId);
    } catch (RuntimeException e) {
      gateway.deleteObject(session.getObjectName());
      throw e;
    }
  }

  private void validateCompletedWordDocument(FileMultipartUpload session) {
    if (!wordDocumentValidator.requiresValidation(session.getOriginalFileName(), session.getFolder())) return;
    try (var input = gateway.getObject(session.getObjectName()).inputStream()) {
      wordDocumentValidator.validate(session.getOriginalFileName(), session.getFolder(), input);
    } catch (IOException e) {
      UncheckedIOException failure = new UncheckedIOException("读取合并后的 Word 文件失败", e);
      abortCompletedObject(session, failure);
      throw failure;
    } catch (RuntimeException e) {
      abortCompletedObject(session, e);
      throw e;
    }
  }

  private void abortCompletedObject(FileMultipartUpload session, RuntimeException originalFailure) {
    session.setUploadStatus(ABORTED);
    try {
      sessionMapper.updateById(session);
    } catch (RuntimeException cleanupFailure) {
      originalFailure.addSuppressed(cleanupFailure);
    }
    try {
      gateway.deleteObject(session.getObjectName());
    } catch (RuntimeException cleanupFailure) {
      originalFailure.addSuppressed(cleanupFailure);
    }
  }

  @Override
  public void abort(Long userId, String uploadId) {
    FileMultipartUpload session = requireOwnedActive(userId, uploadId);
    gateway.abortMultipartUpload(session.getObjectName(), session.getProviderUploadId());
    session.setUploadStatus(ABORTED);
    sessionMapper.updateById(session);
  }

  /**
   * 清理过期分片会话。这是全平台运维任务，需要覆盖全部租户。
   * <p>
   * 定时任务跑在调度线程上，没有租户上下文。此前直接查询，靠
   * {@code TenantLineHandlerImpl} 在"非 Web 线程且上下文缺失"时放行才得以跨租户扫描——
   * 那是隐式依赖兜底行为，等同于不受控的跨租户读写。这里改为显式声明：
   * 扫描用 {@link TenantContext#callWithBypass} 表明"确实要看全部租户"，
   * 每条会话的写回再切回它自身租户，使 UPDATE 带上租户条件。
   */
  @Override
  @Scheduled(fixedDelayString = "${storage.minio.cleanup-interval-ms:3600000}")
  public int abortExpired() {
    List<FileMultipartUpload> expired = TenantContext.callWithBypass(
      () -> sessionMapper.selectList(new LambdaQueryWrapper<FileMultipartUpload>()
                                       .eq(FileMultipartUpload::getUploadStatus, UPLOADING)
                                       .le(FileMultipartUpload::getExpiresTime, LocalDateTime.now())));
    int count = 0;
    for (FileMultipartUpload session : expired) {
      try {
        gateway.abortMultipartUpload(session.getObjectName(), session.getProviderUploadId());
        session.setUploadStatus(EXPIRED);
        // 写回限定在该会话自身租户内，避免以旁路身份无条件改表
        int updated = TenantContext.callWithTenant(session.getOrganizationId(),
                                                   () -> sessionMapper.updateById(session));
        if (updated == 1) {
          count++;
        }
      } catch (RuntimeException ignored) {
        // 下一轮继续重试，避免把未成功中止的会话伪记为已清理。
      }
    }
    return count;
  }

  private FileMultipartUpload requireOwnedActive(Long userId, String uploadId) {
    Long organizationId = tenantResolver.resolve(userId);
    FileMultipartUpload session = sessionMapper.selectOne(new LambdaQueryWrapper<FileMultipartUpload>()
                                                            .eq(FileMultipartUpload::getUploadId, uploadId)
                                                            .eq(FileMultipartUpload::getOrganizationId, organizationId)
                                                            .eq(FileMultipartUpload::getUserId, userId)
                                                            .eq(FileMultipartUpload::getUploadStatus, UPLOADING)
                                                            .gt(FileMultipartUpload::getExpiresTime,
                                                                LocalDateTime.now())
                                                            .last("LIMIT 1"));
    if (session == null) throw error(HttpStatus.NOT_FOUND, 5003, "上传会话不存在或已失效");
    return session;
  }

  private static void validateParts(List<UploadedStoragePart> stored,
                                    List<CompletedStoragePart> submitted, long expectedSize) {
    Map<Integer, UploadedStoragePart> byNumber = new HashMap<>();
    long size = 0;
    for (UploadedStoragePart part : stored) {
      byNumber.put(part.partNumber(), part);
      size += part.size();
    }
    if (stored.size() != submitted.size() || size != expectedSize) {
      throw error(HttpStatus.BAD_REQUEST, 5005, "分片数量或总大小不一致");
    }
    int previous = 0;
    for (CompletedStoragePart part : submitted) {
      UploadedStoragePart actual = byNumber.get(part.partNumber());
      if (part.partNumber() <= previous || actual == null || !actual.etag().equals(part.etag())) {
        throw error(HttpStatus.BAD_REQUEST, 5005, "分片顺序或 ETag 不一致");
      }
      previous = part.partNumber();
    }
  }

  private static String extractSuffix(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase() : "";
  }

  private static BusinessException error(HttpStatus status, int code, String message) {
    return new BusinessException(status, code, message);
  }
}
