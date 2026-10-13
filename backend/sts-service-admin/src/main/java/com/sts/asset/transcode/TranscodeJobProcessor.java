package com.sts.asset.transcode;

import com.sts.admin.tenant.TenantContext;
import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetTranscodeJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class TranscodeJobProcessor {
  private static final Pattern SECRET = Pattern.compile(
    "(?i)(token|secret|password|access[-_]?key|authorization)=([^\\s&]+)");
  private static final Pattern AUTHORIZATION = Pattern.compile(
    "(?i)(authorization\\s*[:=]\\s*)(?:bearer\\s+)?([^\\s,;]+)");
  private static final Pattern URL_QUERY = Pattern.compile(
    "(?i)(https?://[^\\s?]+)\\?[^\\s]+");
  private final AssetTranscodeJobMapper mapper;
  private final TranscodeProviderRegistry registry;
  private final TranscodeResultService resultService;

  /**
   * 处理一个转码任务。
   * <p>
   * 本方法跑在 {@code @Scheduled} 触发的线程池上，不经过 {@code TenantContextFilter}，
   * 因此没有租户上下文。任务实体自带 {@code organizationId}，读到之后 MUST 立刻绑定，
   * 使后续 provider 读素材、写派生文件全都在该租户内进行——否则依赖
   * {@code TenantLineHandlerImpl} 在"非 Web 线程且上下文缺失"时的放行兜底：
   * 读会变成无条件全表查，写会因 {@code organization_id} 非空约束直接失败。
   * <p>
   * {@code claimPending} 与 {@code selectById} 留在上下文之外：调度器需要跨租户抢占待处理任务，
   * 且拿到 job 之前无从得知租户是谁。这两条 SQL 由 Mapper 自带条件控制，不依赖插件。
   */
  public void process(Long jobId) {
    if (mapper.claimPending(jobId) != 1) return;
    AssetTranscodeJob job = mapper.selectById(jobId);
    if (job == null) return;
    TenantContext.runWithTenant(job.getOrganizationId(), () -> processInTenant(job));
  }

  private void processInTenant(AssetTranscodeJob job) {
    TranscodeProvider provider = null;
    TranscodeOutput output = null;
    try {
      resultService.markProcessing(job);
      provider = registry.require(job.getJobType());
      output = provider.execute(job);
      resultService.succeed(job, output);
    } catch (TranscodeFailureException e) {
      cleanup(provider, output);
      log.error("转码任务执行失败 jobId={} assetId={} failureCode={}",
                job.getTranscodeJobId(), job.getAssetId(), e.getFailureCode(), diagnostic(e));
      resultService.fail(job, e.getFailureCode(), sanitize(e.getMessage()));
    } catch (RuntimeException e) {
      cleanup(provider, output);
      log.error("转码任务执行失败 jobId={} assetId={} failureCode=TRANSCODE_ERROR",
                job.getTranscodeJobId(), job.getAssetId(), diagnostic(e));
      resultService.fail(job, "TRANSCODE_ERROR", sanitize(e.getMessage()));
    }
  }

  private static void cleanup(TranscodeProvider provider, TranscodeOutput output) {
    if (provider == null || output == null) return;
    try {
      provider.cleanup(output);
    } catch (RuntimeException ignored) {
    }
  }

  static String sanitize(String reason) {
    String value = reason == null || reason.isBlank() ? "转码执行失败" : reason;
    value = AUTHORIZATION.matcher(value).replaceAll("$1***");
    value = SECRET.matcher(value).replaceAll("$1=***");
    value = URL_QUERY.matcher(value).replaceAll("$1?***");
    return value.length() <= 1000 ? value : value.substring(0, 1000);
  }

  static Throwable diagnostic(Throwable failure) {
    return diagnostic(failure, 0);
  }

  private static Throwable diagnostic(Throwable failure, int depth) {
    String message = failure.getClass().getName() + ": " + sanitize(failure.getMessage());
    RuntimeException safe = new RuntimeException(message);
    safe.setStackTrace(failure.getStackTrace());
    Throwable cause = failure.getCause();
    if (cause != null && cause != failure && depth < 8) {
      safe.initCause(diagnostic(cause, depth + 1));
    }
    return safe;
  }
}
