package com.sts.asset.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.asset.entity.Asset;
import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.support.AssetMapperItConfig;
import com.sts.asset.transcode.TranscodeArtifact;
import com.sts.asset.transcode.TranscodeFailureException;
import com.sts.asset.transcode.TranscodeOutput;
import com.sts.asset.transcode.TranscodeResultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = AssetMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TranscodeResultService.class)
@Transactional
class TranscodeResultServiceIT extends AbstractMysqlIntegrationTest {
  @Autowired
  private AssetMapper assetMapper;
  @Autowired
  private AssetTranscodeJobMapper jobMapper;
  @Autowired
  private TranscodeResultService resultService;
  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void successAtomicallyRegistersDerivativeDurationAndTerminalStates() {
    Long orgId = jdbc.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code='DEMO' AND deleted=0",
      Long.class);
    Asset asset = new Asset();
    asset.setOrganizationId(orgId);
    asset.setAssetCode("RESULT-" + System.nanoTime());
    asset.setAssetName("结果提交测试");
    asset.setAssetType("AUDIO");
    asset.setSourceType("UPLOAD");
    asset.setProcessStatus("PROCESSING");
    asset.setStatus(1);
    assetMapper.insert(asset);
    AssetTranscodeJob job = new AssetTranscodeJob();
    job.setOrganizationId(orgId);
    job.setAssetId(asset.getAssetId());
    job.setJobType("AUDIO_TRANSCODE");
    job.setJobStatus("RUNNING");
    job.setRetryCount(0);
    jobMapper.insert(job);

    resultService.succeed(job, new TranscodeOutput(7,
                                                   3,
                                                   List.of(new TranscodeArtifact("HLS",
                                                                                 "derivative/test/hls/index.m3u8",
                                                                                 "index.m3u8",
                                                                                 1024,
                                                                                 "m3u8")),
                                                   List.of("derivative/test/hls/index.m3u8")));

    assertThat(jobMapper.selectById(job.getTranscodeJobId()).getJobStatus()).isEqualTo("SUCCEEDED");
    Asset updated = assetMapper.selectById(asset.getAssetId());
    assertThat(updated.getProcessStatus()).isEqualTo("SUCCEEDED");
    assertThat(updated.getDurationSeconds()).isEqualTo(7);
    assertThat(updated.getPageCount()).isEqualTo(3);
    assertThat(jdbc.queryForObject(
      "SELECT COUNT(*) FROM sts_asset_derivative WHERE asset_id=? AND derivative_type='HLS' AND deleted=0",
      Integer.class, asset.getAssetId())).isEqualTo(1);
  }

  @Test
  void audioSuccessWithoutHlsDoesNotPersistDerivative() {
    Long orgId = jdbc.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code='DEMO' AND deleted=0",
      Long.class);
    Asset asset = new Asset();
    asset.setOrganizationId(orgId);
    asset.setAssetCode("RESULT-NOHLS-" + System.nanoTime());
    asset.setAssetName("缺 HLS 不得成功");
    asset.setAssetType("AUDIO");
    asset.setSourceType("UPLOAD");
    asset.setProcessStatus("PROCESSING");
    asset.setStatus(1);
    assetMapper.insert(asset);
    AssetTranscodeJob job = new AssetTranscodeJob();
    job.setOrganizationId(orgId);
    job.setAssetId(asset.getAssetId());
    job.setJobType("AUDIO_TRANSCODE");
    job.setJobStatus("RUNNING");
    job.setRetryCount(0);
    jobMapper.insert(job);

    assertThatThrownBy(() -> resultService.succeed(job, new TranscodeOutput(7,
                                                                            List.of(new TranscodeArtifact(
                                                                              "TRANSCODED",
                                                                              "derivative/test/audio.mp3",
                                                                              "audio.mp3",
                                                                              1024,
                                                                              "mp3")),
                                                                            List.of("derivative/test/audio.mp3"))))
      .isInstanceOf(TranscodeFailureException.class)
      .extracting("failureCode")
      .isEqualTo("TRANSCODE_ERROR");

    assertThat(jobMapper.selectById(job.getTranscodeJobId()).getJobStatus()).isEqualTo("RUNNING");
    assertThat(assetMapper.selectById(asset.getAssetId()).getProcessStatus()).isEqualTo("PROCESSING");
    assertThat(jdbc.queryForObject(
      "SELECT COUNT(*) FROM sts_asset_derivative WHERE asset_id=? AND deleted=0",
      Integer.class, asset.getAssetId())).isEqualTo(0);
  }

  /**
   * 上面那条用例是在基类绑定的 DEMO 租户下跑的，因此它证明不了"调度线程能落库"——
   * 原缺陷正是被这一点掩盖。这里补一条：清空上下文后再提交结果，模拟真实的调度线程形态。
   * 归属由 {@code TranscodeJobProcessor} 在调用前绑定，本用例直接显式绑定以隔离验证 succeed 本身。
   */
  @Test
  void succeedWritesDerivativeWhenCalledUnderTheJobTenantWithoutAmbientContext() {
    Long orgId = jdbc.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code='DEMO' AND deleted=0",
      Long.class);
    Asset asset = new Asset();
    asset.setOrganizationId(orgId);
    asset.setAssetCode("RESULT-NOCTX-" + System.nanoTime());
    asset.setAssetName("无环境上下文提交");
    asset.setAssetType("AUDIO");
    asset.setSourceType("UPLOAD");
    asset.setProcessStatus("PROCESSING");
    asset.setStatus(1);
    assetMapper.insert(asset);
    AssetTranscodeJob job = new AssetTranscodeJob();
    job.setOrganizationId(orgId);
    job.setAssetId(asset.getAssetId());
    job.setJobType("AUDIO_TRANSCODE");
    job.setJobStatus("RUNNING");
    job.setRetryCount(0);
    jobMapper.insert(job);

    TenantContext.clear();
    TenantContext.runWithTenant(orgId, () -> resultService.succeed(job, new TranscodeOutput(
      7, null,
      List.of(new TranscodeArtifact("HLS", "derivative/noctx/hls/index.m3u8", "index.m3u8", 1024, "m3u8")),
      List.of("derivative/noctx/hls/index.m3u8"))));

    assertThat(jdbc.queryForObject(
      "SELECT COUNT(*) FROM sts_asset_derivative WHERE asset_id=? AND organization_id=? AND deleted=0",
      Integer.class, asset.getAssetId(), orgId))
      .describedAs("派生文件必须落库并归属任务租户").isEqualTo(1);
  }
}
