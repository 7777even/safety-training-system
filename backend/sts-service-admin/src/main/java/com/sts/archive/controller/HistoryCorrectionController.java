package com.sts.archive.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.archive.dto.HistoryCorrectionRequest;
import com.sts.learner.service.LearnerHistoryService;
import com.sts.learner.vo.LearnerHistoryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 管理更正权限不自动授予，Service再次检查对应来源读取权限和机构。 */
@RestController @RequestMapping("/archive/persons/{userId}/history/{recordType}/{recordId}/corrections") @RequiredArgsConstructor
public class HistoryCorrectionController {
  /** 仅追加的档案更正服务。 */
  private final LearnerHistoryService history;
  /** 授权管理人员追加一条更正，不改正式结果。 */
  @PostMapping @SaCheckPermission("archive:history:correct")
  public LearnerHistoryVO append(@PathVariable Long userId,@PathVariable String recordType,@PathVariable Long recordId,
    @RequestBody @Valid HistoryCorrectionRequest request) { return history.correct(userId,recordType,recordId,request); }
  /** 查询记录原始摘要和更正轨迹。 */
  @GetMapping @SaCheckPermission("archive:history:correction:list")
  public LearnerHistoryVO list(@PathVariable Long userId,@PathVariable String recordType,@PathVariable Long recordId) { return history.corrections(userId,recordType,recordId); }
}
