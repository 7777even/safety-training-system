package com.sts.exam.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.exam.dto.ExamLearnerTaskQuery;
import com.sts.exam.service.ExamLearnerService;
import com.sts.exam.vo.ExamAttemptVO;
import com.sts.exam.vo.ExamLearnerTaskVO;
import com.sts.exam.vo.ExamLearnerNoticeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 学员本人考试任务、封面与作答详情接口。
 */
@RestController
@RequiredArgsConstructor
public class ExamLearnerController {
  private final ExamLearnerService examLearnerService;

  /**
   * 分页查询我的考试任务。
   */
  @GetMapping("/exam/learner/tasks")
  @SaCheckPermission("exam:learner:task:list")
  public PageResult<ExamLearnerTaskVO> pageMyTasks(PageRequest<ExamLearnerTaskQuery> request) {
    return examLearnerService.pageMyTasks(request);
  }

  /** 学员须知沿用本人任务权限，服务端再次校验本机构实际考生关系。 */
  @GetMapping("/exam/learner/exams/{examId}/notice")
  @SaCheckPermission("exam:learner:task:list")
  public ExamLearnerNoticeVO notice(@PathVariable Long examId) { return examLearnerService.getMyNotice(examId); }

  /**
   * 查询我的考试作答。
   */
  @GetMapping("/exam/learner/attempts/{examAttemptId}")
  @SaCheckPermission("exam:learner:attempt:view")
  public ExamAttemptVO getMyAttempt(@PathVariable Long examAttemptId) {
    return examLearnerService.getMyAttempt(examAttemptId);
  }

  /**
   * 查看或下载当前学员可见考试的封面。
   */
  @GetMapping("/exam/learner/exams/{examId}/cover")
  @SaCheckPermission("exam:learner:task:list")
  public ResponseEntity<Resource> cover(
    @PathVariable Long examId,
    @RequestParam(value = "download", defaultValue = "false") boolean download) {
    ExamLearnerService.ExamCoverFile cover = examLearnerService.openCover(examId);
    ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
      .filename(safeFileName(cover.fileName(), examId), StandardCharsets.UTF_8)
      .build();
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(cover.contentType()))
      .contentLength(cover.fileSize())
      .eTag(cover.etag())
      .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
      .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
      .body(cover.resource());
  }

  private static String safeFileName(String value, Long examId) {
    String name = value == null ? "" : value.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
    return name.isEmpty() ? "exam-cover-" + examId : name;
  }
}
