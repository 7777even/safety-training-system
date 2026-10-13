package com.sts.training.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.*;
import com.sts.training.service.*;
import com.sts.training.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 线下培训场次、成绩、附件及奖惩管理接口。
 */
@RestController
@RequiredArgsConstructor
public class TrainingExecutionController {

  /** 只内联浏览器可安全处理的常见佐证类型；其他格式由文件查看器提示不支持。 */
  private static final Set<String> INLINE_EVIDENCE_TYPES = Set.of(
    "application/pdf", "image/png", "image/jpeg", "image/webp", "image/gif", "image/bmp",
    "text/plain", "text/csv", "application/json", "video/mp4", "video/webm", "video/quicktime",
    "audio/mpeg", "audio/wav", "audio/ogg", "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation");

  private final TrainingOfflineSessionService offlineSessionService;
  private final TrainingOfflineScoreService offlineScoreService;
  private final TrainingAttachmentService attachmentService;
  private final TrainingRewardPenaltyService rewardPenaltyService;
  private final TrainingTeacherTaskService teacherTaskService;

  /**
   * 分页查询线下培训场次。
   */
  @GetMapping("/training/offline-sessions")
  @SaCheckPermission("training:plan:list")
  public PageResult<TrainingOfflineSessionVO> pageSessions(PageRequest<TrainingOfflineSessionQuery> request) {
    return offlineSessionService.page(request);
  }

  /**
   * 查询线下培训场次。
   */
  @GetMapping("/training/offline-sessions/{offlineSessionId}")
  @SaCheckPermission("training:plan:list")
  public TrainingOfflineSessionVO getSession(@PathVariable Long offlineSessionId) {
    return offlineSessionService.get(offlineSessionId);
  }

  /**
   * 创建线下培训场次。
   */
  @PostMapping("/training/offline-sessions")
  @SaCheckPermission("training:plan:edit")
  public Long createSession(@RequestBody @Valid TrainingOfflineSessionCreateRequest request) {
    return offlineSessionService.create(request);
  }

  /**
   * 更新线下培训场次。
   */
  @PutMapping("/training/offline-sessions/{offlineSessionId}")
  @SaCheckPermission("training:plan:edit")
  public void updateSession(@PathVariable Long offlineSessionId,
                            @RequestBody @Valid TrainingOfflineSessionUpdateRequest request) {
    offlineSessionService.update(offlineSessionId, request);
  }

  /**
   * 删除线下培训场次。
   */
  @DeleteMapping("/training/offline-sessions/{offlineSessionId}")
  @SaCheckPermission("training:plan:edit")
  public void deleteSession(@PathVariable Long offlineSessionId) {
    offlineSessionService.delete(offlineSessionId);
  }

  /**
   * 开始线下场次。
   */
  @PostMapping("/training/offline-sessions/{offlineSessionId}/start")
  @SaCheckPermission(value = {"training:plan:edit", "training:teacher:task"}, mode = SaMode.OR)
  public void startSession(@PathVariable Long offlineSessionId,
                           @RequestBody @Valid TrainingPublishRequest request) {
    offlineSessionService.start(offlineSessionId, request.getVersion(),
                                StpUtil.getLoginIdAsLong(), StpUtil.hasPermission("training:plan:edit"));
  }

  /**
   * 结束线下场次。
   */
  @PostMapping("/training/offline-sessions/{offlineSessionId}/complete")
  @SaCheckPermission(value = {"training:plan:edit", "training:teacher:task"}, mode = SaMode.OR)
  public void completeSession(@PathVariable Long offlineSessionId,
                              @RequestBody @Valid TrainingPublishRequest request) {
    offlineSessionService.complete(offlineSessionId, request.getVersion(),
                                   StpUtil.getLoginIdAsLong(), StpUtil.hasPermission("training:plan:edit"));
  }

  /**
   * 取消线下场次。
   */
  @PostMapping("/training/offline-sessions/{offlineSessionId}/cancel")
  @SaCheckPermission(value = {"training:plan:edit", "training:teacher:task"}, mode = SaMode.OR)
  public void cancelSession(@PathVariable Long offlineSessionId,
                            @RequestBody @Valid TrainingPublishRequest request) {
    offlineSessionService.cancel(offlineSessionId, request.getVersion(),
                                 StpUtil.getLoginIdAsLong(), StpUtil.hasPermission("training:plan:edit"));
  }

  /**
   * 分页查询线下成绩。
   */
  @GetMapping("/training/offline-scores")
  @SaCheckPermission("training:offline-score:list")
  public PageResult<TrainingOfflineScoreVO> pageOfflineScores(PageRequest<TrainingOfflineScoreQuery> request) {
    return offlineScoreService.page(request);
  }

  /**
   * 新增或更新线下成绩。
   */
  @PostMapping("/training/offline-scores")
  @SaCheckPermission("training:teacher:task")
  public TrainingOfflineScoreVO upsertOfflineScore(@RequestBody @Valid TrainingOfflineScoreUpsertRequest request) {
    return offlineScoreService.upsert(request, StpUtil.getLoginIdAsLong(), false);
  }

  /**
   * 分页查询培训附件。
   */
  @GetMapping("/training/attachments")
  @SaCheckPermission("training:attachment:list")
  public PageResult<TrainingAttachmentVO> pageAttachments(PageRequest<TrainingAttachmentQuery> request) {
    return attachmentService.page(request);
  }

  /**
   * 查询培训附件。
   */
  @GetMapping("/training/attachments/{trainingAttachmentId}")
  @SaCheckPermission("training:attachment:list")
  public TrainingAttachmentVO getAttachment(@PathVariable Long trainingAttachmentId) {
    return attachmentService.get(trainingAttachmentId);
  }

  /**
   * 创建培训附件。
   */
  @PostMapping("/training/attachments")
  @SaCheckPermission("training:attachment:add")
  public Long createAttachment(@RequestBody @Valid TrainingAttachmentCreateRequest request) {
    return attachmentService.create(request);
  }

  /**
   * 更新培训附件。
   */
  @PutMapping("/training/attachments/{trainingAttachmentId}")
  @SaCheckPermission("training:attachment:edit")
  public void updateAttachment(@PathVariable Long trainingAttachmentId,
                               @RequestBody @Valid TrainingAttachmentUpdateRequest request) {
    attachmentService.update(trainingAttachmentId, request);
  }

  /**
   * 删除培训附件。
   */
  @DeleteMapping("/training/attachments/{trainingAttachmentId}")
  @SaCheckPermission("training:attachment:delete")
  public void deleteAttachment(@PathVariable Long trainingAttachmentId) {
    attachmentService.delete(trainingAttachmentId);
  }

  /**
   * 分页查询奖惩记录。
   */
  @GetMapping("/training/reward-penalties")
  @SaCheckPermission("training:reward:list")
  public PageResult<TrainingRewardPenaltyVO> pageRewardPenalties(PageRequest<TrainingRewardPenaltyQuery> request) {
    return rewardPenaltyService.page(request);
  }

  /**
   * 查询奖惩记录。
   */
  @GetMapping("/training/reward-penalties/{rewardPenaltyId}")
  @SaCheckPermission("training:reward:list")
  public TrainingRewardPenaltyVO getRewardPenalty(@PathVariable Long rewardPenaltyId) {
    return rewardPenaltyService.get(rewardPenaltyId);
  }

  /** 按奖惩记录读取佐证原始文件，浏览器可在预览弹窗内展示。 */
  @GetMapping("/training/reward-penalties/{rewardPenaltyId}/evidence")
  @SaCheckPermission("training:reward:list")
  public ResponseEntity<Resource> rewardEvidence(@PathVariable Long rewardPenaltyId) {
    TrainingRewardEvidenceFile file = rewardPenaltyService.openEvidence(rewardPenaltyId);
    return inlineFile(file.resource(), file.fileName(), file.fileSize(), "佐证文件");
  }

  /**
   * 按培训资料记录读取原始文件，浏览器可在预览弹窗内展示；不接受调用方传入的文件 ID。
   */
  @GetMapping("/training/attachments/{trainingAttachmentId}/file")
  @SaCheckPermission("training:attachment:list")
  public ResponseEntity<Resource> attachmentFile(@PathVariable Long trainingAttachmentId) {
    TrainingAttachmentFile file = attachmentService.openFile(trainingAttachmentId);
    return inlineFile(file.resource(), file.fileName(), file.fileSize(), "培训资料");
  }

  /**
   * 统一按内联方式返回受控文件：仅内联白名单类型，其余降级为附件下载，并固定禁止内容嗅探。
   */
  private static ResponseEntity<Resource> inlineFile(Resource resource, String rawName, long fileSize,
                                                     String fallbackName) {
    String name = rawName == null ? fallbackName : rawName.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
    if (name.isBlank()) name = fallbackName;
    MediaType detectedType = MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM);
    MediaType contentType = INLINE_EVIDENCE_TYPES.contains(detectedType.toString())
      ? detectedType : MediaType.APPLICATION_OCTET_STREAM;
    return ResponseEntity.ok()
      .contentType(contentType)
      .contentLength(fileSize)
      .header("X-Content-Type-Options", "nosniff")
      .header(HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.inline().filename(name, StandardCharsets.UTF_8).build().toString())
      .body(resource);
  }

  /**
   * 创建奖惩记录。
   */
  @PostMapping("/training/reward-penalties")
  @SaCheckPermission("training:reward:add")
  public Long createRewardPenalty(@RequestBody @Valid TrainingRewardPenaltyCreateRequest request) {
    return rewardPenaltyService.create(request);
  }

  /**
   * 更新奖惩记录。
   */
  @PutMapping("/training/reward-penalties/{rewardPenaltyId}")
  @SaCheckPermission("training:reward:edit")
  public void updateRewardPenalty(@PathVariable Long rewardPenaltyId,
                                  @RequestBody @Valid TrainingRewardPenaltyUpdateRequest request) {
    rewardPenaltyService.update(rewardPenaltyId, request);
  }

  /**
   * 删除奖惩记录。
   */
  @DeleteMapping("/training/reward-penalties/{rewardPenaltyId}")
  @SaCheckPermission("training:reward:delete")
  public void deleteRewardPenalty(@PathVariable Long rewardPenaltyId) {
    rewardPenaltyService.delete(rewardPenaltyId);
  }

  /**
   * 分页查询教师任务。
   */
  @GetMapping("/training/teacher-tasks")
  @SaCheckPermission("training:teacher:task")
  public PageResult<TrainingTeacherTaskVO> pageTeacherTasks(PageRequest<TrainingTeacherTaskQuery> request) {
    return teacherTaskService.page(request);
  }
}
