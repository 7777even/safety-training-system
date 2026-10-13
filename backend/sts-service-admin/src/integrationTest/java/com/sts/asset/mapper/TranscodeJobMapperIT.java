package com.sts.asset.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.asset.entity.Asset;
import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.support.AssetMapperItConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AssetMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TranscodeJobMapperIT extends AbstractMysqlIntegrationTest {
  @Autowired
  private AssetMapper assetMapper;
  @Autowired
  private AssetTranscodeJobMapper jobMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void conditionalClaimAllowsOnlyOneExecutionAndResultUpdatesAreTerminal() {
    AssetTranscodeJob job = insertJob("PENDING", null);

    assertThat(jobMapper.claimPending(job.getTranscodeJobId())).isEqualTo(1);
    assertThat(jobMapper.claimPending(job.getTranscodeJobId())).isZero();
    assertThat(jobMapper.selectById(job.getTranscodeJobId()).getJobStatus()).isEqualTo("RUNNING");

    assertThat(jobMapper.finishSucceeded(job.getTranscodeJobId())).isEqualTo(1);
    assertThat(jobMapper.finishFailed(job.getTranscodeJobId(), "TIMEOUT", "late timeout")).isZero();
    assertThat(jobMapper.selectById(job.getTranscodeJobId()).getJobStatus()).isEqualTo("SUCCEEDED");
  }

  @Test
  void timeoutQueryReturnsOnlyOldRunningJobs() {
    AssetTranscodeJob old = insertJob("RUNNING", LocalDateTime.now().minusMinutes(31));
    insertJob("RUNNING", LocalDateTime.now().minusMinutes(5));
    insertJob("PENDING", null);

    assertThat(jobMapper.selectTimedOutRunning(LocalDateTime.now().minusMinutes(30), 20))
      .extracting(AssetTranscodeJob::getTranscodeJobId).containsExactly(old.getTranscodeJobId());
  }

  private AssetTranscodeJob insertJob(String status, LocalDateTime startedTime) {
    Long orgId = jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code='DEMO' AND deleted=0",
      Long.class);
    Asset asset = new Asset();
    asset.setOrganizationId(orgId);
    asset.setAssetCode("JOB-" + System.nanoTime());
    asset.setAssetName("转码任务素材");
    asset.setAssetType("VIDEO");
    asset.setSourceType("UPLOAD");
    asset.setProcessStatus("RUNNING".equals(status) ? "PROCESSING" : "UPLOADED");
    asset.setStatus(1);
    assetMapper.insert(asset);

    AssetTranscodeJob job = new AssetTranscodeJob();
    job.setOrganizationId(orgId);
    job.setAssetId(asset.getAssetId());
    job.setJobType("VIDEO_TRANSCODE");
    job.setJobStatus(status);
    job.setStartedTime(startedTime);
    job.setRetryCount(0);
    jobMapper.insert(job);
    return job;
  }
}
