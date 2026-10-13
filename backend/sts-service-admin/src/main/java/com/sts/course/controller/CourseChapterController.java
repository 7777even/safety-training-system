package com.sts.course.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.course.dto.ChapterCreateRequest;
import com.sts.course.dto.ChapterUpdateRequest;
import com.sts.course.service.CourseChapterService;
import com.sts.course.vo.CourseChapterVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程章节管理。业务层强制章节只能挂根级（两层：课程→章节→内容项），
 * 无嵌套子章节能力；排序调整替代"移动到其他章节下"。
 */
@RestController
@RequestMapping("/course/chapters")
@RequiredArgsConstructor
public class CourseChapterController {

  private final CourseChapterService chapterService;

  /**
   * 查询章节列表。
   */
  @GetMapping
  @SaCheckPermission("course:chapter:list")
  public List<CourseChapterVO> listChapters(@RequestParam Long courseId) {
    return chapterService.listChapters(courseId);
  }

  /**
   * 创建章节。
   */
  @PostMapping
  @SaCheckPermission("course:chapter:add")
  public Long createChapter(@RequestBody @Valid ChapterCreateRequest request) {
    return chapterService.createChapter(request);
  }

  /**
   * 更新章节。
   */
  @PutMapping("/{chapterId}")
  @SaCheckPermission("course:chapter:edit")
  public void updateChapter(@PathVariable Long chapterId, @RequestBody @Valid ChapterUpdateRequest request) {
    chapterService.updateChapter(chapterId, request);
  }

  /**
   * 删除章节。
   */
  @DeleteMapping("/{chapterId}")
  @SaCheckPermission("course:chapter:delete")
  public void deleteChapter(@PathVariable Long chapterId) {
    chapterService.deleteChapter(chapterId);
  }
}
