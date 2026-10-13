package com.sts.exam.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.admin.system.dto.UserImportResult;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.util.CsvContent;
import com.sts.exam.dto.*;
import com.sts.exam.service.ExamService;
import com.sts.exam.service.OfflineScoreService;
import com.sts.exam.vo.ExamCandidateVO;
import com.sts.exam.vo.ExamVO;
import com.sts.exam.vo.OfflineScoreRecordResult;
import com.sts.regulation.export.Utf8CsvExporter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 考试安排、考生范围及阅卷人配置管理接口。
 */
@RestController
@RequiredArgsConstructor
public class ExamController {

  private final ExamService examService;
  private final OfflineScoreService offlineScoreService;

  /**
   * 分页查询考试。
   */
  @GetMapping("/exam/exams")
  @SaCheckPermission("exam:exam:list")
  public PageResult<ExamVO> page(PageRequest<ExamQuery> request) {
    return examService.page(request);
  }

  /**
   * 查询考试。
   */
  @GetMapping("/exam/exams/{examId}")
  @SaCheckPermission("exam:exam:list")
  public ExamVO get(@PathVariable Long examId) {
    return examService.get(examId);
  }

  /**
   * 创建考试。
   */
  @PostMapping("/exam/exams")
  @SaCheckPermission("exam:exam:add")
  public Long create(@RequestBody @Valid ExamCreateRequest request) {
    return examService.create(request);
  }

  /**
   * 更新考试。
   */
  @PutMapping("/exam/exams/{examId}")
  @SaCheckPermission("exam:exam:edit")
  public void update(@PathVariable Long examId, @RequestBody @Valid ExamUpdateRequest request) {
    examService.update(examId, request);
  }

  /**
   * 删除考试。
   */
  @DeleteMapping("/exam/exams/{examId}")
  @SaCheckPermission("exam:exam:delete")
  public void delete(@PathVariable Long examId) {
    examService.delete(examId);
  }

  /**
   * 发布考试。
   */
  @PostMapping("/exam/exams/{examId}/publish")
  @SaCheckPermission("exam:publish")
  public void publish(@PathVariable Long examId) {
    examService.publish(examId);
  }

  /**
   * 选择并绑定具体考生。
   */
  @PostMapping("/exam/exams/{examId}/candidates/bind-selection")
  @SaCheckPermission("exam:exam:edit")
  public List<ExamCandidateVO> bindSelection(@PathVariable Long examId,
                                             @RequestBody @Valid BindSelectionRequest request) {
    return examService.bindSelection(examId, request);
  }

  /**
   * 分页查询考生。
   */
  @GetMapping("/exam/exams/{examId}/candidates")
  @SaCheckPermission("exam:exam:list")
  public PageResult<ExamCandidateVO> pageCandidates(@PathVariable Long examId,
                                                    PageRequest<ExamCandidateQuery> request) {
    return examService.pageCandidates(examId, request);
  }

  /**
   * 从 CSV 批量导入考生（当前租户范围内按登录名/手机号匹配用户）。
   */
  @PostMapping("/exam/exams/{examId}/candidates/import")
  @SaCheckPermission("exam:exam:import")
  public UserImportResult importCandidates(@PathVariable Long examId,
                                           @RequestParam("file") MultipartFile file) {
    return examService.importCandidates(examId, file);
  }

  /**
   * 导出考生名单为 CSV。
   */
  @GetMapping("/exam/exams/{examId}/candidates/export")
  @SaCheckPermission("exam:exam:export")
  public ResponseEntity<byte[]> exportCandidates(@PathVariable Long examId) {
    CsvContent data = examService.exportCandidates(examId);
    return Utf8CsvExporter.download("exam_candidates.csv", "", data.headers(), data.rows());
  }

  /**
   * 查询阅卷人列表。
   */
  @GetMapping("/exam/exams/{examId}/reviewers")
  @SaCheckPermission("exam:exam:list")
  public java.util.List<com.sts.exam.vo.ExamReviewerVO> listReviewers(@PathVariable Long examId) {
    return examService.listReviewers(examId);
  }

  /**
   * 替换阅卷人。
   */
  @PutMapping("/exam/exams/{examId}/reviewers")
  @SaCheckPermission("exam:exam:edit")
  public void replaceReviewers(@PathVariable Long examId,
                               @RequestBody @Valid ExamReviewersReplaceRequest request) {
    examService.replaceReviewers(examId, request);
  }

  /**
   * 录入线下考试成绩。
   */
  @PostMapping("/exam/exams/{examId}/offline-scores")
  @SaCheckPermission("exam:score:publish")
  public OfflineScoreRecordResult recordOfflineScores(@PathVariable Long examId,
                                                      @RequestBody @Valid OfflineScoresRequest request) {
    return offlineScoreService.record(examId, request);
  }
}
