package com.sts.training.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.sts.training.service.OfflineClassStatisticsService;
import com.sts.training.vo.OfflineClassStatisticsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
/** 线下课堂统计，同时要求计划和成绩读取权限。 */
@RestController
@RequiredArgsConstructor
public class OfflineClassStatisticsController {
  private final OfflineClassStatisticsService service;
  /** 展示场次内容及学员考试结果。 */
  @GetMapping("/training/offline-sessions/{offlineSessionId}/statistics")
  @SaCheckPermission(value={"training:plan:list","training:offline-score:list"},mode=SaMode.AND)
  public OfflineClassStatisticsVO statistics(@PathVariable Long offlineSessionId) {return service.statistics(offlineSessionId);}
}
