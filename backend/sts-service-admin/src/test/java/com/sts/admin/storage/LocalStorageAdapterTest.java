package com.sts.admin.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalStorageAdapterTest {

  @TempDir
  Path tempDir;

  @Test
  void generalUploadPrefixesCurrentOrganizationRoot() {
    StorageProperties properties = new StorageProperties();
    properties.getLocal().setBasePath(tempDir.toString());
    TenantObjectKeyService objectKeys = mock(TenantObjectKeyService.class);
    when(objectKeys.forCurrentTenant(anyString())).thenAnswer(invocation -> "ACME/" + invocation.getArgument(0));
    LocalStorageAdapter service = new LocalStorageAdapter(properties, objectKeys);
    MockMultipartFile file = new MockMultipartFile("file", "plan.pdf", "application/pdf", "content".getBytes());

    FileInfo result = service.upload(file, "general");

    assertThat(result.fileKey()).startsWith("ACME/general/").endsWith(".pdf");
    assertThat(tempDir.resolve(result.fileKey())).exists();
  }

  @Test
  void courseCoverUploadUsesDedicatedYearMonthPrefix() {
    StorageProperties properties = new StorageProperties();
    properties.getLocal().setBasePath(tempDir.toString());
    TenantObjectKeyService objectKeys = mock(TenantObjectKeyService.class);
    when(objectKeys.forCurrentTenant(anyString())).thenAnswer(invocation -> "ACME/" + invocation.getArgument(0));
    LocalStorageAdapter service = new LocalStorageAdapter(properties, objectKeys);
    MockMultipartFile file = new MockMultipartFile("file", "cover.webp", "image/webp", "content".getBytes());

    FileInfo result = service.upload(file, "course-cover");

    assertThat(result.fileKey()).matches("ACME/course-cover/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.webp");
    assertThat(tempDir.resolve(result.fileKey())).exists();
  }
}
