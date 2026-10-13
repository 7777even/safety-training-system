package com.sts.asset.transcode;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.asset.entity.Asset;
import com.sts.asset.entity.AssetDerivative;
import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetDerivativeMapper;
import com.sts.asset.mapper.AssetMapper;
import com.sts.asset.support.AssetMapperItConfig;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 转码执行链路的租户上下文（Testcontainers 真 MySQL）。
 * <p>
 * <b>本用例存在的理由</b>：转码跑在 {@code @Scheduled} + 线程池上，没有任何租户上下文；
 * 而 {@link AbstractMysqlIntegrationTest} 在 {@code @BeforeEach} 绑定了 DEMO 租户——
 * 正是这一点让原缺陷在既有 IT 里完全不可见：`TranscodeResultServiceIT` 一直是绿的，
 * 生产却因为 {@code organization_id} 非空约束写不进派生文件，素材被置为 FAILED。
 * <p>
 * 因此下面每个用例都 MUST 先 {@link TenantContext#clear()}，还原调度线程的真实形态。
 * 删掉那行 clear()，用例就退化成什么也证明不了。
 */
@SpringBootTest(classes = AssetMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TranscodeResultService.class, TranscodeJobProcessor.class, TranscodeProviderRegistry.class,
  TranscodeTenantContextIT.RecordingProviderConfig.class})
@Transactional
class TranscodeTenantContextIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private AssetDerivativeMapper derivativeMapper;
  @Autowired
  private TranscodeJobProcessor processor;
  @Autowired
  private RecordingProvider provider;
  @Autowired
  private JdbcTemplate jdbc;

  /**
   * 仿真 Provider：像 {@code FfmpegProvider} 一样先按 ID 读素材（受隔离表），再产出派生文件。
   * 读不到素材时按转码失败处理，与真实 Provider 的行为一致。
   */
  @RequiredArgsConstructor
  static class RecordingProvider implements TranscodeProvider {
    private final AssetMapper assetMapper;
    /**
     * 记录 execute 期间观察到的租户上下文，用于断言绑定确实生效。
     */
    volatile Long observedTenantId;

    @Override
    public boolean supports(String jobType) {
      return true;
    }

    @Override
    public TranscodeOutput execute(AssetTranscodeJob job) {
      observedTenantId = TenantContext.currentTenantIdOrNull();
      Asset asset = assetMapper.selectById(job.getAssetId());
      if (asset == null) {
        throw new TranscodeFailureException("SOURCE_MISSING", "素材不存在或不属于当前租户");
      }
      return new TranscodeOutput(7, List.of(new TranscodeArtifact(
        "HLS", "derivative/" + job.getAssetId() + "/hls/index.m3u8", "index.m3u8", 1024L, "m3u8")),
                                 List.of());
    }
  }

  @TestConfiguration
  static class RecordingProviderConfig {
    @Bean
    RecordingProvider recordingProvider(AssetMapper assetMapper) {
      return new RecordingProvider(assetMapper);
    }
  }

  private Long otherTenantId() {
    jdbc.update("INSERT INTO sts_org_organization (org_code, org_name, org_type, status, description)"
                  + " VALUES ('TC-OTHER', '另一家企业', 'ENTERPRISE', 1, '')");
    return jdbc.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code = 'TC-OTHER'", Long.class);
  }

  /**
   * 直接写库造数，绕开插件，模拟这些行本就属于目标租户。
   */
  private Long insertAsset(Long organizationId, String code) {
    jdbc.update("""
                  INSERT INTO sts_asset_asset
                    (organization_id, asset_code, asset_name, asset_type, source_type, process_status, status)
                  VALUES (?, ?, ?, 'AUDIO', 'UPLOAD', 'PROCESSING', 1)
                  """, organizationId, code, "素材-" + code);
    return jdbc.queryForObject(
      "SELECT asset_id FROM sts_asset_asset WHERE asset_code = ?", Long.class, code);
  }

  private Long insertPendingJob(Long organizationId, Long assetId) {
    jdbc.update("""
                  INSERT INTO sts_asset_transcode_job
                    (organization_id, asset_id, job_type, job_status, retry_count)
                  VALUES (?, ?, 'AUDIO_TRANSCODE', 'PENDING', 0)
                  """, organizationId, assetId);
    return jdbc.queryForObject(
      "SELECT transcode_job_id FROM sts_asset_transcode_job WHERE asset_id = ?", Long.class, assetId);
  }

  @Test
  @DisplayName("调度线程无租户上下文时，转码成功仍能写入派生文件且归属正确")
  void process_writesDerivativeUnderJobTenantWithoutAmbientContext() {
    Long other = otherTenantId();
    Long assetId = insertAsset(other, "TC-OK");
    Long jobId = insertPendingJob(other, assetId);

    TenantContext.clear();   // 调度线程的真实形态
    processor.process(jobId);

    assertThat(provider.observedTenantId)
      .describedAs("执行期间 MUST 已绑定任务所属租户").isEqualTo(other);

    Integer derivativeCount = jdbc.queryForObject(
      "SELECT COUNT(*) FROM sts_asset_derivative WHERE asset_id = ? AND organization_id = ?",
      Integer.class, assetId, other);
    assertThat(derivativeCount)
      .describedAs("无上下文时派生文件必须仍能落库，且归属任务所属租户").isEqualTo(1);

    assertThat(jdbc.queryForObject("SELECT process_status FROM sts_asset_asset WHERE asset_id = ?",
                                   String.class, assetId))
      .describedAs("写派生失败会被 Processor 吞成 FAILED，这里必须是 SUCCEEDED").isEqualTo("SUCCEEDED");
    assertThat(jdbc.queryForObject(
      "SELECT job_status FROM sts_asset_transcode_job WHERE transcode_job_id = ?", String.class, jobId))
      .isEqualTo("SUCCEEDED");
  }

  @Test
  @DisplayName("派生文件不跨租户可见：别家租户查不到")
  void derivative_isNotVisibleToAnotherTenant() {
    Long other = otherTenantId();
    Long assetId = insertAsset(other, "TC-ISOLATED");
    Long jobId = insertPendingJob(other, assetId);

    TenantContext.clear();
    processor.process(jobId);

    assertThat(TenantContext.callWithTenant(demoTenantId(),
                                            () -> derivativeMapper.selectList(new LambdaQueryWrapper<AssetDerivative>()
                                                                                .eq(AssetDerivative::getAssetId,
                                                                                    assetId))))
      .describedAs("DEMO 租户不得看到 TC-OTHER 的派生文件").isEmpty();
    assertThat(TenantContext.callWithTenant(other,
                                            () -> derivativeMapper.selectList(new LambdaQueryWrapper<AssetDerivative>()
                                                                                .eq(AssetDerivative::getAssetId,
                                                                                    assetId))))
      .hasSize(1);
  }

  @Test
  @DisplayName("处理结束后不残留租户上下文，线程池复用不串租户")
  void process_doesNotLeakTenantContextAfterCompletion() {
    Long other = otherTenantId();
    Long assetId = insertAsset(other, "TC-LEAK");
    Long jobId = insertPendingJob(other, assetId);

    TenantContext.clear();
    processor.process(jobId);

    assertThat(TenantContext.isPresent())
      .describedAs("处理完 MUST 恢复原上下文（此处为无），否则同一线程处理下一个任务会串租户")
      .isFalse();
  }

  @Test
  @DisplayName("provider 读素材同样受租户约束：任务与素材租户不一致时按失败处理")
  void process_failsWhenJobTenantDoesNotMatchAssetTenant() {
    Long other = otherTenantId();
    // 素材属于 DEMO，任务却声称属于 TC-OTHER：绑定上下文后 provider 读不到该素材
    Long assetId = insertAsset(demoTenantId(), "TC-MISMATCH");
    Long jobId = insertPendingJob(other, assetId);

    TenantContext.clear();
    processor.process(jobId);

    assertThat(jdbc.queryForObject(
      "SELECT job_status FROM sts_asset_transcode_job WHERE transcode_job_id = ?", String.class, jobId))
      .describedAs("绑定上下文后跨租户读素材会失败，不应静默按别家素材转码").isEqualTo("FAILED");
  }
}
