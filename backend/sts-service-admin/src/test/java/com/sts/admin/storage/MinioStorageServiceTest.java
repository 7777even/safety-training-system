package com.sts.admin.storage;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MinioStorageServiceTest {

  @Test
  void uploadUsesGeneratedSourceKeyAndPrivateBucket() {
    S3StorageGateway gateway = mock(S3StorageGateway.class);
    TenantObjectKeyService objectKeys = mock(TenantObjectKeyService.class);
    when(objectKeys.forCurrentTenant(anyString())).thenAnswer(invocation -> "ACME/" + invocation.getArgument(0));
    doNothing().when(gateway).putObject(anyString(), any(), anyLong(), anyString());
    MinioStorageService service = new MinioStorageService(gateway, objectKeys);
    MockMultipartFile file = new MockMultipartFile(
      "file", "培训视频.mp4", "video/mp4", "content".getBytes());

    FileInfo result = service.upload(file, "asset");

    assertThat(result.fileKey()).startsWith("ACME/source/").endsWith(".mp4");
    assertThat(result.fileKey()).doesNotContain("培训视频").doesNotContain("..");
    assertThat(result.fileName()).isEqualTo("培训视频.mp4");
    verify(gateway).putObject(eq(result.fileKey()), any(), eq(7L), eq("video/mp4"));
  }

  @Test
  void courseCoverUploadUsesDedicatedYearMonthPrefix() {
    S3StorageGateway gateway = mock(S3StorageGateway.class);
    TenantObjectKeyService objectKeys = mock(TenantObjectKeyService.class);
    when(objectKeys.forCurrentTenant(anyString())).thenAnswer(invocation -> "ACME/" + invocation.getArgument(0));
    MinioStorageService service = new MinioStorageService(gateway, objectKeys);
    MockMultipartFile file = new MockMultipartFile(
      "file", "课程封面.PNG", "image/png", "content".getBytes());

    FileInfo result = service.upload(file, "course-cover");

    assertThat(result.fileKey())
      .matches("ACME/course-cover/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png");
    verify(gateway).putObject(eq(result.fileKey()), any(), eq(7L), eq("image/png"));
  }

  @Test
  void downloadAndDeleteDelegateByGeneratedObjectKey() throws Exception {
    S3StorageGateway gateway = mock(S3StorageGateway.class);
    when(gateway.getObject("source/2026/08/id.mp4"))
      .thenReturn(new StorageObject(new ByteArrayInputStream("abc".getBytes()), 3, "video/mp4"));
    MinioStorageService service = new MinioStorageService(gateway, mock(TenantObjectKeyService.class));

    assertThat(service.download("source/2026/08/id.mp4").contentLength()).isEqualTo(3);
    service.delete("source/2026/08/id.mp4");

    verify(gateway).deleteObject("source/2026/08/id.mp4");
  }
}
