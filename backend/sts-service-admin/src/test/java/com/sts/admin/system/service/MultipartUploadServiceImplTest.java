package com.sts.admin.system.service;

import com.sts.admin.entity.FileMultipartUpload;
import com.sts.admin.mapper.FileMultipartUploadMapper;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.*;
import com.sts.admin.system.dto.MultipartCompleteRequest;
import com.sts.admin.system.dto.MultipartInitRequest;
import com.sts.admin.system.vo.MultipartCompleteVO;
import com.sts.admin.system.vo.MultipartInitVO;
import com.sts.admin.tenant.CurrentTenantResolver;
import com.sts.admin.tenant.TenantContext;
import com.sts.asset.service.AssetOriginalFileSizeValidator;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MultipartUploadServiceImplTest {

  private FileMultipartUploadMapper sessionMapper;
  private SysFileInfoMapper fileInfoMapper;
  private S3StorageGateway gateway;
  private AssetWordDocumentValidator wordValidator;
  private TenantObjectKeyService objectKeys;
  private MultipartUploadServiceImpl service;

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @BeforeEach
  void setUp() {
    sessionMapper = mock(FileMultipartUploadMapper.class);
    fileInfoMapper = mock(SysFileInfoMapper.class);
    gateway = mock(S3StorageGateway.class);
    wordValidator = mock(AssetWordDocumentValidator.class);
    objectKeys = mock(TenantObjectKeyService.class);
    when(objectKeys.forCurrentTenant(anyString())).thenAnswer(invocation -> "ACME/" + invocation.getArgument(0));
    CurrentTenantResolver tenantResolver = mock(CurrentTenantResolver.class);
    when(tenantResolver.resolve(anyLong())).thenReturn(10L);
    MultipartUploadPersistenceService persistenceService =
      new MultipartUploadPersistenceService(sessionMapper, fileInfoMapper);
    StorageProperties storageProperties = new StorageProperties();
    service = new MultipartUploadServiceImpl(
      sessionMapper, gateway, tenantResolver, objectKeys, persistenceService, wordValidator,
      new AssetOriginalFileSizeValidator(), storageProperties);
  }

  @Test
  void initCreatesOwnedSessionAndReturnsPartSize() {
    when(sessionMapper.selectOne(any())).thenReturn(null);
    when(gateway.createMultipartUpload(anyString(), eq("video/mp4"))).thenReturn("provider-1");
    MultipartInitRequest request = request("video.mp4", 20_000_000L, "a".repeat(64));

    MultipartInitVO result = service.init(7L, request);

    assertThat(result.getUploadId()).isNotBlank();
    assertThat(result.getPartSize()).isEqualTo(10 * 1024 * 1024L);
    assertThat(result.getUploadedParts()).isEmpty();
    verify(sessionMapper).insert(argThat((FileMultipartUpload session) ->
                                           session.getOrganizationId().equals(10L)
                                             && session.getUserId().equals(7L)
                                             && session.getProviderUploadId().equals("provider-1")
                                             && session.getObjectName().startsWith("ACME/source/")));
  }

  @Test
  void initRejectsReservedCourseCoverFolder() {
    MultipartInitRequest request = request("cover.png", 100L, "a".repeat(64));
    request.setFolder("course-cover");

    assertThatThrownBy(() -> service.init(7L, request))
      .isInstanceOfSatisfying(BusinessException.class,
                              error -> assertThat(error.getCode()).isEqualTo(5001));
    verifyNoInteractions(gateway);
    verify(sessionMapper, never()).insert(any(FileMultipartUpload.class));
  }

  @Test
  void initRejectsAssetVideoLargerThan2GbBeforeCreatingSession() {
    MultipartInitRequest request = request("video.mp4", 2L * 1024 * 1024 * 1024 + 1, "a".repeat(64));

    assertThatThrownBy(() -> service.init(7L, request))
      .isInstanceOfSatisfying(BusinessException.class, error -> {
        assertThat(error.getCode()).isEqualTo(5007);
        assertThat(error.getMessage()).isEqualTo("音视频原始文件不能超过 2GB");
      });
    verifyNoInteractions(gateway);
    verify(sessionMapper, never()).insert(any(FileMultipartUpload.class));
  }

  @Test
  void initResumesOnlySameOwnersActiveSession() {
    FileMultipartUpload session = activeSession(10L, 7L);
    when(sessionMapper.selectOne(any())).thenReturn(session);
    when(gateway.listParts(session.getObjectName(), "provider-1"))
      .thenReturn(List.of(new UploadedStoragePart(1, "etag-1", 10L)));

    MultipartInitVO result = service.init(7L, request("video.mp4", 20L, "b".repeat(64)));

    assertThat(result.getUploadId()).isEqualTo("public-1");
    assertThat(result.getUploadedParts()).containsExactly(1);
    verify(gateway, never()).createMultipartUpload(anyString(), anyString());
    verify(sessionMapper, never()).insert(any(FileMultipartUpload.class));
  }

  @Test
  void uploadPartRejectsSessionOwnedByAnotherUserWithoutCallingStorage() {
    when(sessionMapper.selectOne(any())).thenReturn(null);
    MockMultipartFile part = new MockMultipartFile("file", "part", "application/octet-stream", new byte[]{1});

    assertThatThrownBy(() -> service.uploadPart(8L, "public-1", 1, part))
      .isInstanceOf(BusinessException.class);
    verifyNoInteractions(gateway);
  }

  @Test
  void completeVerifiesSizeAndChecksumBeforeRegisteringFile() {
    FileMultipartUpload session = activeSession(10L, 7L);
    session.setFileSize(3L);
    session.setChecksum("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    when(sessionMapper.selectOne(any())).thenReturn(session);
    when(gateway.listParts(session.getObjectName(), "provider-1"))
      .thenReturn(List.of(new UploadedStoragePart(1, "etag-1", 3L)));
    when(gateway.objectSize(session.getObjectName())).thenReturn(3L);
    when(gateway.sha256(session.getObjectName()))
      .thenReturn("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    doAnswer(invocation -> {
      assertThat(TenantContext.currentTenantIdOrNull()).isEqualTo(session.getOrganizationId());
      com.sts.admin.entity.SysFileInfo file = invocation.getArgument(0);
      file.setFileInfoId(99L);
      return 1;
    }).when(fileInfoMapper).insert(any(com.sts.admin.entity.SysFileInfo.class));

    TenantContext.setTenantId(session.getOrganizationId());
    MultipartCompleteVO result = service.complete(
      7L, "public-1", new MultipartCompleteRequest(List.of(new CompletedStoragePart(1, "etag-1"))));

    assertThat(result.getFileInfoId()).isNotNull();
    assertThat(TenantContext.requireTenantId()).isEqualTo(session.getOrganizationId());
    verify(gateway).completeMultipartUpload(eq(session.getObjectName()), eq("provider-1"), anyList());
    verify(fileInfoMapper).insert(argThat((com.sts.admin.entity.SysFileInfo file) ->
                                            file.getObjectName().equals(session.getObjectName())));
    verify(sessionMapper).updateByIdWithVersionCheck(argThat((FileMultipartUpload updated) ->
                                                               "COMPLETED".equals(updated.getUploadStatus())));
  }

  @Test
  void abortRejectsExpiredOrForeignSessionAndNeverTouchesStorage() {
    when(sessionMapper.selectOne(any())).thenReturn(null);

    assertThatThrownBy(() -> service.abort(9L, "public-1"))
      .isInstanceOf(BusinessException.class);

    verifyNoInteractions(gateway);
  }

  @Test
  void checksumMismatchDeletesCompletedObjectAndDoesNotRegisterFile() {
    FileMultipartUpload session = activeSession(10L, 7L);
    session.setFileSize(3L);
    session.setChecksum("a".repeat(64));
    when(sessionMapper.selectOne(any())).thenReturn(session);
    when(gateway.listParts(session.getObjectName(), "provider-1"))
      .thenReturn(List.of(new UploadedStoragePart(1, "etag-1", 3L)));
    when(gateway.objectSize(session.getObjectName())).thenReturn(3L);
    when(gateway.sha256(session.getObjectName())).thenReturn("b".repeat(64));

    assertThatThrownBy(() -> service.complete(
      7L, "public-1", new MultipartCompleteRequest(List.of(new CompletedStoragePart(1, "etag-1")))))
      .isInstanceOf(BusinessException.class);

    verify(gateway).deleteObject(session.getObjectName());
    verify(fileInfoMapper, never()).insert(any(com.sts.admin.entity.SysFileInfo.class));
    verify(sessionMapper).updateById(argThat((FileMultipartUpload updated) ->
                                               "ABORTED".equals(updated.getUploadStatus())));
  }

  @Test
  void incompatibleDocxDeletesCompletedObjectAndAbortsSession() {
    FileMultipartUpload session = activeSession(10L, 7L);
    session.setOriginalFileName("legacy.docx");
    session.setFileSize(3L);
    session.setChecksum("a".repeat(64));
    when(sessionMapper.selectOne(any())).thenReturn(session);
    when(gateway.listParts(session.getObjectName(), "provider-1"))
      .thenReturn(List.of(new UploadedStoragePart(1, "etag-1", 3L)));
    when(gateway.objectSize(session.getObjectName())).thenReturn(3L);
    when(gateway.sha256(session.getObjectName())).thenReturn("a".repeat(64));
    when(wordValidator.requiresValidation("legacy.docx", "asset")).thenReturn(true);
    when(gateway.getObject(session.getObjectName()))
      .thenReturn(new StorageObject(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, "application/octet-stream"));
    doThrow(new BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST, 5006, "请另存为标准 DOCX"))
      .when(wordValidator).validate(eq("legacy.docx"), eq("asset"), any());

    assertThatThrownBy(() -> service.complete(
      7L, "public-1", new MultipartCompleteRequest(List.of(new CompletedStoragePart(1, "etag-1")))))
      .isInstanceOfSatisfying(BusinessException.class,
                              error -> assertThat(error.getCode()).isEqualTo(5006));

    verify(gateway).deleteObject(session.getObjectName());
    verify(fileInfoMapper, never()).insert(any(com.sts.admin.entity.SysFileInfo.class));
    verify(sessionMapper).updateById(argThat((FileMultipartUpload updated) ->
                                               "ABORTED".equals(updated.getUploadStatus())));
  }

  @Test
  void incompatibleDocxPreservesValidationErrorAndAttemptsBothCompensationsWhenDeleteFails() {
    FileMultipartUpload session = activeSession(10L, 7L);
    session.setOriginalFileName("legacy.docx");
    session.setFileSize(3L);
    session.setChecksum("a".repeat(64));
    when(sessionMapper.selectOne(any())).thenReturn(session);
    when(gateway.listParts(session.getObjectName(), "provider-1"))
      .thenReturn(List.of(new UploadedStoragePart(1, "etag-1", 3L)));
    when(gateway.objectSize(session.getObjectName())).thenReturn(3L);
    when(gateway.sha256(session.getObjectName())).thenReturn("a".repeat(64));
    when(wordValidator.requiresValidation("legacy.docx", "asset")).thenReturn(true);
    when(gateway.getObject(session.getObjectName()))
      .thenReturn(new StorageObject(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, "application/octet-stream"));
    BusinessException validationError =
      new BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST, 5006, "请另存为标准 DOCX");
    doThrow(validationError).when(wordValidator).validate(eq("legacy.docx"), eq("asset"), any());
    doThrow(new IllegalStateException("MinIO delete failed")).when(gateway).deleteObject(session.getObjectName());

    assertThatThrownBy(() -> service.complete(
      7L, "public-1", new MultipartCompleteRequest(List.of(new CompletedStoragePart(1, "etag-1")))))
      .isSameAs(validationError)
      .satisfies(error -> assertThat(error.getSuppressed()).hasSize(1));

    verify(gateway).deleteObject(session.getObjectName());
    verify(sessionMapper).updateById(argThat((FileMultipartUpload updated) ->
                                               "ABORTED".equals(updated.getUploadStatus())));
    verify(fileInfoMapper, never()).insert(any(com.sts.admin.entity.SysFileInfo.class));
  }

  @Test
  void cleanupAbortsExpiredSessionsAndMarksThemExpired() {
    FileMultipartUpload session = activeSession(10L, 7L);
    session.setExpiresTime(LocalDateTime.now().minusMinutes(1));
    when(sessionMapper.selectList(any())).thenReturn(List.of(session));
    // 只有写回确实影响到该租户的行才计入已清理，因此必须显式桩为成功
    when(sessionMapper.updateById(any(FileMultipartUpload.class))).thenReturn(1);

    assertThat(service.abortExpired()).isEqualTo(1);

    verify(gateway).abortMultipartUpload(session.getObjectName(), session.getProviderUploadId());
    verify(sessionMapper).updateById(argThat((FileMultipartUpload updated) ->
                                               "EXPIRED".equals(updated.getUploadStatus())));
  }

  private MultipartInitRequest request(String name, long size, String checksum) {
    MultipartInitRequest request = new MultipartInitRequest();
    request.setFileName(name);
    request.setContentType("video/mp4");
    request.setFileSize(size);
    request.setChecksum(checksum);
    request.setFolder("asset");
    return request;
  }

  private FileMultipartUpload activeSession(Long organizationId, Long userId) {
    FileMultipartUpload session = new FileMultipartUpload();
    session.setMultipartUploadId(1L);
    session.setUploadId("public-1");
    session.setOrganizationId(organizationId);
    session.setUserId(userId);
    session.setProviderUploadId("provider-1");
    session.setBucketName("sts-private");
    session.setObjectName("source/2026/08/id.mp4");
    session.setFolder("asset");
    session.setOriginalFileName("video.mp4");
    session.setContentType("video/mp4");
    session.setFileSize(20L);
    session.setChecksum("b".repeat(64));
    session.setPartSize(10L);
    session.setUploadStatus("UPLOADING");
    session.setExpiresTime(LocalDateTime.now().plusHours(1));
    return session;
  }
}
