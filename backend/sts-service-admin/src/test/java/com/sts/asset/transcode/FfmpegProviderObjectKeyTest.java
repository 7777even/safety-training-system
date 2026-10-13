package com.sts.asset.transcode;

import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.S3StorageGateway;
import com.sts.admin.storage.TenantObjectKeyService;
import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FfmpegProviderObjectKeyTest {

  @Test
  void derivativePrefixUsesCurrentOrganizationRoot() {
    TenantObjectKeyService objectKeys = mock(TenantObjectKeyService.class);
    when(objectKeys.forCurrentTenant(anyString()))
      .thenAnswer(invocation -> "ACME/" + invocation.getArgument(0));
    FfmpegProvider provider = new FfmpegProvider(
      mock(AssetMapper.class), mock(SysFileInfoMapper.class), mock(S3StorageGateway.class),
      mock(FfmpegCommandFactory.class), mock(ExternalProcessRunner.class),
      new TranscodeProperties(), objectKeys);
    AssetTranscodeJob job = new AssetTranscodeJob();
    job.setAssetId(20L);
    job.setTranscodeJobId(9L);

    assertThat(provider.prefix(job, "hls")).isEqualTo("ACME/derivative/20/9/hls");
  }
}
