package com.sts.training.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageResult;
import com.sts.training.service.TrainingClassStatisticsService;
import com.sts.training.vo.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
/** 班级统计与下钻复用培训计划读取权限。 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/training/statistics/classes")
@SaCheckPermission("training:plan:list")
public class TrainingClassStatisticsController {
  private final TrainingClassStatisticsService service;
  /** 汇总当前可见计划中的部门班级。 */
  @GetMapping public TrainingClassStatisticsVO statistics(@RequestParam(required=false) Long trainingPlanId) {return service.statistics(trainingPlanId);}
  /** 分页查看班级人员。 */
  @GetMapping("/people") public PageResult<TrainingClassPersonVO> people(@RequestParam(required=false) Long trainingPlanId,@RequestParam(required=false) Long deptId,
    @RequestParam(defaultValue="false") boolean unassignedOnly,@RequestParam(required=false) @Pattern(regexp="ONLINE|OFFLINE") String mode,@RequestParam(required=false) String learnerKeyword,
    @RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="20") @Min(1) int pageSize) {
    return service.people(trainingPlanId,deptId,unassignedOnly,mode,learnerKeyword,pageNum,pageSize);
  }
}
