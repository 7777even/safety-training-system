package com.sts.training.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.training.dto.*;
import com.sts.training.service.TrainingMatrixService;
import com.sts.training.vo.TrainingMatrixVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
/** 岗位矩阵维护与计划草稿创建接口。 */
@RestController @RequiredArgsConstructor public class TrainingMatrixController {
  /** 岗位矩阵业务。 */ private final TrainingMatrixService service;
  /** 查看本机构矩阵。 */ @GetMapping("/training/matrices") @SaCheckPermission("training:matrix:list")
  public List<TrainingMatrixVO> list() { return service.list(); }
  /** 新增要求。 */ @PostMapping("/training/matrices") @SaCheckPermission("training:matrix:edit")
  public Long create(@Valid @RequestBody TrainingMatrixRequest request) { return service.save(null, request); }
  /** 修改要求。 */ @PutMapping("/training/matrices/{matrixId}") @SaCheckPermission("training:matrix:edit")
  public void update(@PathVariable Long matrixId, @Valid @RequestBody TrainingMatrixRequest request) { service.save(matrixId, request); }
  /** 管理员确认后创建草稿，必须同时具备计划新增权限。 */
  @PostMapping("/training/matrices/create-plan") @SaCheckPermission({"training:matrix:list", "training:plan:add"})
  public Long createPlan(@Valid @RequestBody TrainingMatrixPlanRequest request) { return service.createPlan(request); }
  /** 查看已有计划冻结矩阵来源。 */
  @GetMapping("/training/plans/{trainingPlanId}/matrix-source") @SaCheckPermission("training:plan:list")
  public List<TrainingMatrixVO> source(@PathVariable Long trainingPlanId) { return service.source(trainingPlanId); }
}
