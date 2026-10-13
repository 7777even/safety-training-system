package com.sts.learner.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sts.common.api.*;
import com.sts.learner.service.LearnerHistoryService;
import com.sts.learner.vo.LearnerHistoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 本人统一档案，不接收userId，不能追加更正。 */
@RestController @RequestMapping("/learner/history") @SaCheckLogin @RequiredArgsConstructor
public class LearnerHistoryController {
  /** 只读聚合和本人可见轨迹。 */
  private final LearnerHistoryService history;
  /** 按来源、名称和日期分页历史摘要。 */
  @GetMapping public PageResult<LearnerHistoryVO> page(PageRequest<LearnerHistoryService.Query> request) { return history.mine(request); }
  /** 返回原始摘要、当前更正展示与追溯记录。 */
  @GetMapping("/{recordType}/{recordId}") public LearnerHistoryVO detail(@PathVariable String recordType,@PathVariable Long recordId) { return history.myDetail(recordType,recordId); }
}
