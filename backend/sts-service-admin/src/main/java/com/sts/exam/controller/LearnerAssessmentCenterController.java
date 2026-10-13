package com.sts.exam.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sts.common.api.*;
import com.sts.exam.service.LearnerAssessmentCenterService;
import com.sts.exam.service.LearnerAssessmentCenterService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 本人正式考试/随堂测试聚合查询；不修改现有考试和课程作答协议。 */
@RestController @RequiredArgsConstructor @SaCheckLogin
@RequestMapping("/exam/learner")
public class LearnerAssessmentCenterController {
  private final LearnerAssessmentCenterService service;
  /** 分页获取两类本人考核。 */
  @GetMapping("/assessment-tasks") public PageResult<Task> tasks(PageRequest<Query> request) { return service.tasks(request); }
  /** 获取本人允许回看的历史考卷。 */
  @GetMapping("/attempt-history") public List<History> history(@RequestParam(required = false) Long candidateId,
    @RequestParam(required = false) Long enrollmentId, @RequestParam(required = false) Long itemId) { return service.history(candidateId, enrollmentId, itemId); }
}
