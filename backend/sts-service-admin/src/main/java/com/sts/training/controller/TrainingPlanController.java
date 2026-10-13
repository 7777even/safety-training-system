package com.sts.training.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.*;
import com.sts.training.service.TrainingPlanService;
import com.sts.training.vo.TrainingGenerateExecutionsResultVO;
import com.sts.training.vo.TrainingParticipantVO;
import com.sts.training.vo.TrainingPlanVO;
import com.sts.training.vo.TrainingPublishPrecheckVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 培训计划、阶段、评价表单及参训人员管理接口。
 */
@RestController
@RequiredArgsConstructor
public class TrainingPlanController {

  private final TrainingPlanService trainingPlanService;

  /**
   * 分页查询培训计划。
   */
  @GetMapping("/training/plans")
  @SaCheckPermission("training:plan:list")
  public PageResult<TrainingPlanVO> page(PageRequest<TrainingPlanQuery> request) {
    return trainingPlanService.page(request);
  }

  /**
   * 查询培训计划。
   */
  @GetMapping("/training/plans/{trainingPlanId}")
  @SaCheckPermission("training:plan:list")
  public TrainingPlanVO get(@PathVariable Long trainingPlanId) {
    return trainingPlanService.get(trainingPlanId);
  }

  /**
   * 创建培训计划。
   */
  @PostMapping("/training/plans")
  @SaCheckPermission("training:plan:add")
  public Long create(@RequestBody @Valid TrainingPlanCreateRequest request) {
    return trainingPlanService.create(request);
  }

  /**
   * 更新培训计划。
   */
  @PutMapping("/training/plans/{trainingPlanId}")
  @SaCheckPermission("training:plan:edit")
  public void update(@PathVariable Long trainingPlanId,
                     @RequestBody @Valid TrainingPlanUpdateRequest request) {
    trainingPlanService.update(trainingPlanId, request);
  }

  /**
   * 删除培训计划。
   */
  @DeleteMapping("/training/plans/{trainingPlanId}")
  @SaCheckPermission("training:plan:delete")
  public void delete(@PathVariable Long trainingPlanId) {
    trainingPlanService.delete(trainingPlanId);
  }

  /**
   * 从模板覆盖复制课表到草稿计划。
   */
  @PostMapping("/training/plans/{trainingPlanId}/apply-template")
  @SaCheckPermission("training:plan:edit")
  public void applyTemplate(@PathVariable Long trainingPlanId,
                            @RequestBody @Valid TrainingPlanApplyTemplateRequest request) {
    trainingPlanService.applyTemplate(trainingPlanId, request);
  }

  /**
   * 替换阶段。
   */
  @PutMapping("/training/plans/{trainingPlanId}/stages")
  @SaCheckPermission("training:plan:edit")
  public void replaceStages(@PathVariable Long trainingPlanId,
                            @RequestBody @Valid TrainingPlanStagesReplaceRequest request) {
    trainingPlanService.replaceStages(trainingPlanId, request);
  }

  /**
   * 绑定评价表单。
   */
  @PutMapping("/training/plans/{trainingPlanId}/evaluation-forms")
  @SaCheckPermission("training:plan:edit")
  public void bindEvaluationForms(@PathVariable Long trainingPlanId,
                                  @RequestBody @Valid TrainingPlanEvaluationFormBindRequest request) {
    trainingPlanService.bindEvaluationForms(trainingPlanId, request);
  }

  /**
   * 发布培训计划。
   */
  @PostMapping("/training/plans/{trainingPlanId}/publish")
  @SaCheckPermission("training:plan:publish")
  public void publish(@PathVariable Long trainingPlanId,
                      @RequestBody @Valid TrainingPublishRequest request) {
    trainingPlanService.publish(trainingPlanId, request.getVersion());
  }

  /**
   * 发布预检：校验资源、执行配置、必修内容和参训人，不改变计划状态。
   */
  @PostMapping("/training/plans/{trainingPlanId}/publish-precheck")
  @SaCheckPermission("training:plan:publish")
  public TrainingPublishPrecheckVO precheck(@PathVariable Long trainingPlanId) {
    return trainingPlanService.precheck(trainingPlanId);
  }

  /**
   * 开始培训计划。
   */
  @PostMapping("/training/plans/{trainingPlanId}/start")
  @SaCheckPermission("training:plan:publish")
  public void start(@PathVariable Long trainingPlanId,
                    @RequestBody @Valid TrainingPublishRequest request) {
    trainingPlanService.start(trainingPlanId, request.getVersion());
  }

  /**
   * 结束培训计划并冻结结果。
   */
  @PostMapping("/training/plans/{trainingPlanId}/end")
  @SaCheckPermission("training:plan:publish")
  public void end(@PathVariable Long trainingPlanId,
                  @RequestBody @Valid TrainingPublishRequest request) {
    trainingPlanService.end(trainingPlanId, request.getVersion());
  }

  /**
   * 取消培训计划。
   */
  @PostMapping("/training/plans/{trainingPlanId}/cancel")
  @SaCheckPermission("training:plan:publish")
  public void cancel(@PathVariable Long trainingPlanId,
                     @RequestBody @Valid TrainingPublishRequest request) {
    trainingPlanService.cancel(trainingPlanId, request.getVersion());
  }

  /**
   * 查询参与人员列表。
   */
  @GetMapping("/training/plans/{trainingPlanId}/participants")
  @SaCheckPermission("training:participant:list")
  public List<TrainingParticipantVO> listParticipants(@PathVariable Long trainingPlanId) {
    return trainingPlanService.listParticipants(trainingPlanId);
  }

  /**
   * 分配参训人员。
   */
  @PostMapping("/training/plans/{trainingPlanId}/participants/assign")
  @SaCheckPermission("training:participant:assign")
  public TrainingParticipantVO assignParticipant(@PathVariable Long trainingPlanId,
                                                   @RequestBody @Valid TrainingParticipantAssignRequest request) {
    return trainingPlanService.assignParticipant(trainingPlanId, request);
  }

  /**
   * 选择并绑定具体参训人员。
   */
  @PostMapping("/training/plans/{trainingPlanId}/participants/bind-selection")
  @SaCheckPermission("training:participant:assign")
  public List<TrainingParticipantVO> bindSelection(@PathVariable Long trainingPlanId,
                                                   @RequestBody @Valid BindTrainingSelectionRequest request) {
    return trainingPlanService.bindSelection(trainingPlanId, request);
  }

  /**
   * 生成培训执行实例。
   */
  @PostMapping("/training/plans/{trainingPlanId}/generate-executions")
  @SaCheckPermission("training:plan:publish")
  public TrainingGenerateExecutionsResultVO generateExecutions(@PathVariable Long trainingPlanId) {
    return trainingPlanService.generateExecutions(trainingPlanId);
  }
}
