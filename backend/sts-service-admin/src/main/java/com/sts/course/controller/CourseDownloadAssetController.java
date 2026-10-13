package com.sts.course.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.sts.course.dto.DownloadAssetReplaceRequest;
import com.sts.course.service.CourseDownloadAssetService;
import com.sts.course.vo.DownloadAssetVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程可下载附件清单管理接口。
 */
@RestController
@RequestMapping("/course/courses/{courseId}/download-assets")
@RequiredArgsConstructor
public class CourseDownloadAssetController {

  private final CourseDownloadAssetService downloadAssetService;

  /**
   * 查询课程的可下载附件列表。
   */
  @GetMapping
  @SaCheckPermission(value = {"course:course:list", "course:item:list"}, mode = SaMode.OR)
  public List<DownloadAssetVO> listByCourse(@PathVariable Long courseId) {
    return downloadAssetService.listByCourse(courseId);
  }

  /**
   * 替换课程的全部可下载附件。
   */
  @PutMapping
  @SaCheckPermission("course:course:edit")
  public void replaceAll(@PathVariable Long courseId,
                         @RequestBody @Valid DownloadAssetReplaceRequest request) {
    downloadAssetService.replaceAll(courseId, request);
  }
}
