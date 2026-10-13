package com.sts.training.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.exam.vo.ExamAttemptVO;
import com.sts.training.dto.TrainingExamAttemptStartRequest;
import com.sts.training.dto.TrainingLearnerEnterRequest;
import com.sts.training.service.TrainingAttachmentFile;
import com.sts.training.service.TrainingCoverFile;
import com.sts.training.service.TrainingWorkflowService;
import com.sts.training.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 培训执行生成、学员任务、教师任务及结果同步接口。
 */
@RestController
@RequiredArgsConstructor
public class TrainingWorkflowController {

  private final TrainingWorkflowService workflowService;

  /** 学员侧允许内联预览的类型白名单；其余类型降级为八位流，避免浏览器按自身判断直接渲染。 */
  private static final Set<String> LEARNER_INLINE_TYPES = Set.of(
    "application/pdf", "image/png", "image/jpeg", "image/webp", "image/gif", "image/bmp",
    "text/plain", "text/csv", "application/json", "video/mp4", "video/webm", "video/quicktime",
    "audio/mpeg", "audio/wav", "audio/ogg", "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation");

  /**
   * 移除参训人员。
   */
  @DeleteMapping("/training/plans/{trainingPlanId}/participants/{trainingParticipantId}")
  @SaCheckPermission("training:participant:assign")
  public void removeParticipant(@PathVariable Long trainingPlanId,
                                @PathVariable Long trainingParticipantId) {
    workflowService.removeParticipant(trainingPlanId, trainingParticipantId);
  }

  /**
   * 查询教师任务。
   */
  @GetMapping("/training/teacher-tasks/{offlineSessionId}")
  @SaCheckPermission("training:teacher:task")
  public TrainingTeacherTaskDetailVO getTeacherTask(@PathVariable Long offlineSessionId) {
    return workflowService.getTeacherTask(offlineSessionId, StpUtil.getLoginIdAsLong());
  }

  /**
   * 查询培训执行实例列表。
   */
  @GetMapping("/training/plans/{trainingPlanId}/executions")
  @SaCheckPermission("training:plan:list")
  public List<TrainingExecutionLinkVO> listExecutions(@PathVariable Long trainingPlanId) {
    return workflowService.listExecutions(trainingPlanId);
  }

  /**
   * 为培训计划生成或补齐执行实例。
   */
  @PostMapping("/training/plans/{trainingPlanId}/contents/{planContentId}/provision")
  @SaCheckPermission("training:plan:publish")
  public TrainingExecutionLinkVO provision(@PathVariable Long trainingPlanId,
                                           @PathVariable Long planContentId) {
    return workflowService.provision(trainingPlanId, planContentId);
  }

  /**
   * 学员从培训上下文统一进入课程、考试、题库或线下场次。
   */
  @PostMapping("/training/learner/participants/{trainingParticipantId}/contents/{planContentId}/enter")
  @SaCheckPermission("training:learner:task")
  public TrainingLearnerEnterVO enter(@PathVariable Long trainingParticipantId,
                                      @PathVariable Long planContentId,
                                      @RequestBody(required = false) TrainingLearnerEnterRequest request) {
    return workflowService.enter(trainingParticipantId, planContentId,
                                 request == null ? new TrainingLearnerEnterRequest() : request,
                                 StpUtil.getLoginIdAsLong());
  }

  /** 读取培训理论考试须知；该操作不创建作答。 */
  @GetMapping("/training/learner/participants/{trainingParticipantId}/contents/{planContentId}/exam-notice")
  @SaCheckPermission("training:learner:task")
  public TrainingLearnerExamNoticeVO getLearnerExamNotice(@PathVariable Long trainingParticipantId,
                                                           @PathVariable Long planContentId) {
    return workflowService.getLearnerExamNotice(trainingParticipantId, planContentId,
                                                StpUtil.getLoginIdAsLong());
  }

  /**
   * 开始培训工作流。
   */
  @PostMapping("/training/learner/participants/{trainingParticipantId}/contents/{planContentId}/exam-attempts")
  @SaCheckPermission("training:learner:task")
  public ExamAttemptVO startExam(@PathVariable Long trainingParticipantId,
                                 @PathVariable Long planContentId,
                                 @RequestBody @Valid TrainingExamAttemptStartRequest request) {
    return workflowService.startExam(trainingParticipantId, planContentId, request,
                                     StpUtil.getLoginIdAsLong());
  }

  /**
   * 查询学员任务列表。
   */
  @GetMapping("/training/learner/tasks")
  @SaCheckPermission("training:learner:task")
  public List<TrainingLearnerTaskVO> listLearnerTasks() {
    return workflowService.listLearnerTasks(StpUtil.getLoginIdAsLong());
  }

  /**
   * 查看或下载当前学员可见培训计划的类型封面。
   */
  @GetMapping("/training/learner/plans/{trainingPlanId}/cover")
  @SaCheckPermission("training:learner:task")
  public ResponseEntity<Resource> learnerPlanCover(
    @PathVariable Long trainingPlanId,
    @RequestParam(value = "download", defaultValue = "false") boolean download) {
    TrainingCoverFile cover = workflowService.openLearnerPlanCover(trainingPlanId, StpUtil.getLoginIdAsLong());
    ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
      .filename(safeFileName(cover.fileName(), trainingPlanId), StandardCharsets.UTF_8)
      .build();
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(cover.contentType()))
      .contentLength(cover.fileSize())
      .eTag(cover.etag())
      .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
      .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
      .body(cover.resource());
  }

  /**
   * 查询学员参训详情。
   */
  @GetMapping("/training/learner/participants/{trainingParticipantId}")
  @SaCheckPermission("training:learner:task")
  public TrainingLearnerParticipantDetailVO getLearnerParticipant(
    @PathVariable Long trainingParticipantId) {
    return workflowService.getLearnerParticipant(trainingParticipantId, StpUtil.getLoginIdAsLong());
  }

  /**
   * 查询当前学员本人的线下场次详情。
   */
  @GetMapping("/training/learner/offline-sessions/{offlineSessionId}")
  @SaCheckPermission("training:learner:task")
  public TrainingLearnerOfflineSessionVO getLearnerOfflineSession(
    @PathVariable Long offlineSessionId) {
    return workflowService.getLearnerOfflineSession(offlineSessionId, StpUtil.getLoginIdAsLong());
  }

  /**
   * 查询当前学员本人参训计划下可读的一般资料；主体固定为登录学员，不接受覆盖主体的参数。
   */
  @GetMapping("/training/learner/participants/{trainingParticipantId}/materials")
  @SaCheckPermission("training:learner:task")
  public List<TrainingLearnerMaterialVO> listLearnerMaterials(@PathVariable Long trainingParticipantId) {
    return workflowService.listLearnerMaterials(trainingParticipantId, StpUtil.getLoginIdAsLong());
  }

  /**
   * 受控读取本人参训计划下一般资料的原件，供页面在线预览；不提供下载入口。
   */
  @GetMapping("/training/learner/participants/{trainingParticipantId}/materials/{trainingAttachmentId}/file")
  @SaCheckPermission("training:learner:task")
  public ResponseEntity<Resource> learnerMaterialFile(@PathVariable Long trainingParticipantId,
                                                      @PathVariable Long trainingAttachmentId) {
    TrainingAttachmentFile file = workflowService.openLearnerMaterialFile(
      trainingParticipantId, trainingAttachmentId, StpUtil.getLoginIdAsLong());
    return inlineMaterial(file.resource(), file.fileName(), file.fileSize());
  }

  /**
   * 同步培训参与人的执行结果。
   */
  @PostMapping("/training/plans/{trainingPlanId}/results/synchronize")
  @SaCheckPermission("training:result:synchronize")
  public List<TrainingParticipantResultsVO> synchronizeResults(@PathVariable Long trainingPlanId) {
    return workflowService.synchronizeResults(trainingPlanId);
  }

  /**
   * 查询培训结果。
   */
  @GetMapping("/training/plans/{trainingPlanId}/participants/{trainingParticipantId}/results")
  @SaCheckPermission("training:result:list")
  public TrainingParticipantResultsVO getResults(@PathVariable Long trainingPlanId,
                                                 @PathVariable Long trainingParticipantId) {
    return workflowService.getResults(trainingPlanId, trainingParticipantId);
  }

  /**
   * 查询已结束培训的正式结果：计划汇总、通过/未通过/不完整人数、学员成绩、奖惩和基础统计。
   */
  @GetMapping("/training/plans/{trainingPlanId}/ended-result")
  @SaCheckPermission("training:result:list")
  public TrainingEndedResultVO getEndedResult(@PathVariable Long trainingPlanId) {
    return workflowService.getEndedResult(trainingPlanId);
  }

  private static String safeFileName(String value, Long trainingPlanId) {
    String name = value == null ? "" : value.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
    return name.isEmpty() ? "training-plan-cover-" + trainingPlanId : name;
  }

  /**
   * 学员侧资料只按**内联**方式返回，供页面在线预览；白名单外的类型降级为八位流并禁止内容嗅探。
   * <p>不提供 {@code attachment} 下载分支，学员侧没有下载入口。
   */
  private static ResponseEntity<Resource> inlineMaterial(Resource resource, String rawName, long fileSize) {
    String name = rawName == null ? "" : rawName.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
    if (name.isEmpty()) {
      name = "培训资料";
    }
    MediaType detectedType = MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM);
    MediaType contentType = LEARNER_INLINE_TYPES.contains(detectedType.toString())
      ? detectedType : MediaType.APPLICATION_OCTET_STREAM;
    return ResponseEntity.ok()
      .contentType(contentType)
      .contentLength(fileSize)
      .header("X-Content-Type-Options", "nosniff")
      .header(HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.inline().filename(name, StandardCharsets.UTF_8).build().toString())
      .body(resource);
  }
}
