package com.sts.training.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.TrainingTypeCreateRequest;
import com.sts.training.dto.TrainingTypeQuery;
import com.sts.training.dto.TrainingTypeUpdateRequest;
import com.sts.training.service.TrainingCoverFile;
import com.sts.training.service.TrainingTypeService;
import com.sts.training.vo.TrainingTypeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 培训类型管理接口。
 */
@RestController
@RequiredArgsConstructor
public class TrainingTypeController {

  private final TrainingTypeService trainingTypeService;

  /**
   * 分页查询培训类型。
   */
  @GetMapping("/training/types")
  @SaCheckPermission("training:type:list")
  public PageResult<TrainingTypeVO> page(PageRequest<TrainingTypeQuery> request) {
    return trainingTypeService.page(request);
  }

  /**
   * 查询培训类型。
   */
  @GetMapping("/training/types/{trainingTypeId}")
  @SaCheckPermission("training:type:list")
  public TrainingTypeVO get(@PathVariable Long trainingTypeId) {
    return trainingTypeService.get(trainingTypeId);
  }

  /**
   * 创建培训类型。
   */
  @PostMapping("/training/types")
  @SaCheckPermission("training:type:add")
  public Long create(@RequestBody @Valid TrainingTypeCreateRequest request) {
    return trainingTypeService.create(request);
  }

  /**
   * 更新培训类型。
   */
  @PutMapping("/training/types/{trainingTypeId}")
  @SaCheckPermission("training:type:edit")
  public void update(@PathVariable Long trainingTypeId,
                     @RequestBody @Valid TrainingTypeUpdateRequest request) {
    trainingTypeService.update(trainingTypeId, request);
  }

  /**
   * 删除培训类型。
   */
  @DeleteMapping("/training/types/{trainingTypeId}")
  @SaCheckPermission("training:type:delete")
  public void delete(@PathVariable Long trainingTypeId) {
    trainingTypeService.delete(trainingTypeId);
  }

  /**
   * 查看或下载培训类型封面。
   */
  @GetMapping("/training/types/{trainingTypeId}/cover")
  @SaCheckPermission("training:type:list")
  public ResponseEntity<Resource> cover(
    @PathVariable Long trainingTypeId,
    @RequestParam(value = "download", defaultValue = "false") boolean download) {
    TrainingCoverFile cover = trainingTypeService.openCover(trainingTypeId);
    ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
      .filename(safeFileName(cover.fileName(), trainingTypeId), StandardCharsets.UTF_8)
      .build();
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(cover.contentType()))
      .contentLength(cover.fileSize())
      .eTag(cover.etag())
      .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
      .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
      .body(cover.resource());
  }

  private static String safeFileName(String value, Long trainingTypeId) {
    String name = value == null ? "" : value.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
    return name.isEmpty() ? "training-type-cover-" + trainingTypeId : name;
  }
}
