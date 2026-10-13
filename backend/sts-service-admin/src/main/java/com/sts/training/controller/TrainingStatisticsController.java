package com.sts.training.controller;
import cn.dev33.satoken.annotation.*;
import com.sts.common.api.PageResult;
import com.sts.training.service.TrainingStatisticsService;
import com.sts.training.vo.*;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
/** 培训统计同时验证计划及正式结果读取权限。 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/training/statistics/plans")
@SaCheckPermission(value={"training:plan:list","training:result:list"},mode=SaMode.AND)
public class TrainingStatisticsController {
  private final TrainingStatisticsService service;
  /** 分页统计当前可见培训计划。 */
  @GetMapping public PageResult<TrainingPlanStatisticsVO> statistics(@RequestParam(required=false) String planName,@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="20") @Min(1) int pageSize) {return service.statistics(planName,pageNum,pageSize);}
  /** 计划进度及完成人员名单。 */
  @GetMapping("/{trainingPlanId}/people") public PageResult<TrainingProgressPersonVO> people(@PathVariable Long trainingPlanId,@RequestParam(defaultValue="false") boolean completedOnly,@RequestParam(required=false) String learnerKeyword,@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="20") @Min(1) int pageSize) {return service.people(trainingPlanId,completedOnly,learnerKeyword,pageNum,pageSize);}
}
