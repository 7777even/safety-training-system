package com.sts.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.system.dto.MultipartCompleteRequest;
import com.sts.admin.system.dto.MultipartInitRequest;
import com.sts.admin.system.service.MultipartUploadService;
import com.sts.admin.system.vo.MultipartCompleteVO;
import com.sts.admin.system.vo.MultipartInitVO;
import com.sts.admin.system.vo.MultipartPartVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 大文件分片上传的初始化、分片接收、合并及取消接口。
 */
@RestController
@RequestMapping("/system/files/multipart")
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
public class SysFileMultipartController {
  private final MultipartUploadService service;

  /**
   * 初始化大文件分片上传。
   */
  @PostMapping("/init")
  @SaCheckPermission("sys:file:upload")
  public MultipartInitVO init(@RequestBody @Valid MultipartInitRequest request) {
    return service.init(StpUtil.getLoginIdAsLong(), request);
  }

  /**
   * 上传指定编号的文件分片。
   */
  @PostMapping("/{uploadId}/parts/{partNumber}")
  @SaCheckPermission("sys:file:upload")
  public MultipartPartVO uploadPart(@PathVariable String uploadId,
                                    @PathVariable @Min(1) Integer partNumber,
                                    @RequestParam("file") MultipartFile file) {
    return service.uploadPart(StpUtil.getLoginIdAsLong(), uploadId, partNumber, file);
  }

  /**
   * 合并已上传的文件分片。
   */
  @PostMapping("/{uploadId}/complete")
  @SaCheckPermission("sys:file:upload")
  public MultipartCompleteVO complete(@PathVariable String uploadId,
                                      @RequestBody @Valid MultipartCompleteRequest request) {
    return service.complete(StpUtil.getLoginIdAsLong(), uploadId, request);
  }

  /**
   * 取消分片上传。
   */
  @DeleteMapping("/{uploadId}")
  @SaCheckPermission("sys:file:upload")
  public void abort(@PathVariable String uploadId) {
    service.abort(StpUtil.getLoginIdAsLong(), uploadId);
  }
}
