package com.sts.asset.controller;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.admin.tenant.TenantContext;
import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetTranscodeJobMapper;
import com.sts.asset.transcode.OneTimeSourceTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供转码任务读取原始素材的内部接口。
 */
@RestController
@RequiredArgsConstructor
public class InternalTranscodeSourceController {
  private final OneTimeSourceTokenService tokens;
  private final AssetTranscodeJobMapper jobMapper;
  private final SysFileInfoMapper fileInfoMapper;
  private final StorageService storage;

  /**
   * 读取转码任务所需的原始素材。
   */
  @GetMapping("/internal/asset/transcode-sources/{token}")
  public ResponseEntity<Resource> source(@PathVariable String token) {
    var grant = tokens.consume(token);
    if (grant == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    return TenantContext.callWithTenant(grant.organizationId(), () -> sourceInTenant(grant));
  }

  private ResponseEntity<Resource> sourceInTenant(OneTimeSourceTokenService.SourceGrant grant) {
    AssetTranscodeJob job = jobMapper.selectById(grant.jobId());
    SysFileInfo file = fileInfoMapper.selectById(grant.fileInfoId());
    if (job == null || !"RUNNING".equals(job.getJobStatus()) || file == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    return ResponseEntity.ok(storage.download(file.getObjectName()));
  }
}
