package com.sts.asset.controller;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.admin.tenant.TenantContext;
import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetTranscodeJobMapper;
import com.sts.asset.transcode.OneTimeSourceTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalTranscodeSourceControllerTest {

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  void sourceQueriesAndDownloadsInsideGrantTenantThenClearsContext() {
    Fixture fixture = fixture();
    when(fixture.tokens.consume("token"))
      .thenReturn(new OneTimeSourceTokenService.SourceGrant(7L, 11L, 13L));
    when(fixture.jobMapper.selectById(11L)).thenAnswer(invocation -> {
      assertThat(TenantContext.requireTenantId()).isEqualTo(7L);
      AssetTranscodeJob job = new AssetTranscodeJob();
      job.setJobStatus("RUNNING");
      return job;
    });
    when(fixture.fileInfoMapper.selectById(13L)).thenAnswer(invocation -> {
      assertThat(TenantContext.requireTenantId()).isEqualTo(7L);
      SysFileInfo file = new SysFileInfo();
      file.setObjectName("source/document.docx");
      return file;
    });
    when(fixture.storage.download("source/document.docx")).thenAnswer(invocation -> {
      assertThat(TenantContext.requireTenantId()).isEqualTo(7L);
      return new ByteArrayResource(new byte[]{1, 2, 3});
    });

    assertThat(fixture.controller.source("token").getStatusCode().value()).isEqualTo(200);
    assertThat(TenantContext.isPresent()).isFalse();
  }

  @Test
  void sourceClearsGrantTenantWhenLookupFails() {
    Fixture fixture = fixture();
    when(fixture.tokens.consume("token"))
      .thenReturn(new OneTimeSourceTokenService.SourceGrant(7L, 11L, 13L));
    when(fixture.jobMapper.selectById(11L)).thenThrow(new IllegalStateException("database unavailable"));

    assertThatThrownBy(() -> fixture.controller.source("token"))
      .isInstanceOf(IllegalStateException.class);
    assertThat(TenantContext.isPresent()).isFalse();
  }

  private static Fixture fixture() {
    OneTimeSourceTokenService tokens = mock(OneTimeSourceTokenService.class);
    AssetTranscodeJobMapper jobMapper = mock(AssetTranscodeJobMapper.class);
    SysFileInfoMapper fileInfoMapper = mock(SysFileInfoMapper.class);
    StorageService storage = mock(StorageService.class);
    return new Fixture(tokens, jobMapper, fileInfoMapper, storage,
                       new InternalTranscodeSourceController(tokens, jobMapper, fileInfoMapper, storage));
  }

  private record Fixture(OneTimeSourceTokenService tokens,
                         AssetTranscodeJobMapper jobMapper,
                         SysFileInfoMapper fileInfoMapper,
                         StorageService storage,
                         InternalTranscodeSourceController controller) {
  }
}
