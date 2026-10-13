package com.sts.exam.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.*;
import com.sts.common.exception.BusinessException;
import com.sts.exam.dto.ExamStatisticsQuery;
import com.sts.exam.service.*;
import com.sts.exam.vo.*;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 当前租户考试考情、人员下钻与同范围导出。 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/exam/exams/{examId}/statistics")
public class ExamStatisticsController {
  private final ExamStatisticsService service;
  /** 考情汇总。 */
  @GetMapping
  @SaCheckPermission("exam:exam:list")
  public ExamStatisticsVO statistics(@PathVariable Long examId) { return service.statistics(examId); }
  /** 人员分页，缺考名单与成绩明细共用。 */
  @GetMapping("/people")
  @SaCheckPermission("exam:exam:list")
  public PageResult<ExamStatisticsPersonVO> people(@PathVariable Long examId,
      @RequestParam(defaultValue="1") @Min(1) int pageNum,
      @RequestParam(defaultValue="20") @Min(1) int pageSize, ExamStatisticsQuery query) {
    return service.page(examId,new PageRequest<>(pageNum,pageSize,query));
  }
  /** 选中人员或筛选全部的成绩批量导出；拒绝不在当前结果中的人员。 */
  @GetMapping("/export")
  @SaCheckPermission("exam:exam:list")
  public ResponseEntity<byte[]> export(@PathVariable Long examId, ExamStatisticsQuery query,
      @RequestParam(required=false) List<Long> candidateIds) {
    var rows = service.people(examId,query);
    if (candidateIds!=null && !candidateIds.isEmpty()) {
      var visible = new HashSet<Long>(); rows.forEach(r -> visible.add(r.examCandidateId()));
      if (!visible.containsAll(candidateIds)) throw new BusinessException(HttpStatus.FORBIDDEN,1003,"导出人员不在当前可见结果内");
      rows = rows.stream().filter(r -> candidateIds.contains(r.examCandidateId())).toList();
    }
    return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
      .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("考试成绩.xlsx",StandardCharsets.UTF_8).build().toString())
      .body(ExamStatisticsXlsx.write(rows));
  }
}
