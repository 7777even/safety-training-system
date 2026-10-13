package com.sts.exam.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.exam.entity.*;
import com.sts.exam.mapper.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/** 本人须知只读取本机构真实考生，不泄漏管理字段或题目。 */
class ExamLearnerNoticeTest {
  @Test void noticeRequiresOwnCandidateAndActiveExamInCurrentTenant() {
    var candidates = mock(ExamCandidateMapper.class); var exams = mock(ExamMapper.class);
    var executions = mock(AssessmentExecutionService.class);
    var service = new ExamLearnerServiceImpl(candidates, exams, null, null, null, null, null, null, executions, null, null);
    var exam = new Exam(); exam.setExamId(1L); exam.setOrganizationId(10L); exam.setStatus(1);
    exam.setPublishStatus("PUBLISHED"); exam.setExamName("安全考试"); exam.setNoticeContent("认真作答");
    var execution = new AssessmentExecution(); execution.setAssessmentExecutionId(2L); execution.setOrganizationId(10L);
    execution.setExamId(1L); execution.setSourceType("STANDALONE_EXAM"); execution.setExecutionStatus("READY");
    var candidate = new ExamCandidate(); candidate.setUserId(7L); candidate.setOrganizationId(10L);
    when(exams.selectById(1L)).thenReturn(exam); when(executions.findStandaloneByExamId(1L)).thenReturn(execution);
    when(candidates.selectOne(any())).thenReturn(candidate);
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L); TenantContext.setTenantId(10L);
      assertThat(service.getMyNotice(1L).noticeContent()).isEqualTo("认真作答");
      candidate.setUserId(8L); assertThatThrownBy(() -> service.getMyNotice(1L)).isInstanceOf(BusinessException.class);
      candidate.setUserId(7L); candidate.setOrganizationId(11L);
      assertThatThrownBy(() -> service.getMyNotice(1L)).isInstanceOf(BusinessException.class);
      candidate.setOrganizationId(10L); exam.setPublishStatus("CANCELLED");
      assertThatThrownBy(() -> service.getMyNotice(1L)).isInstanceOf(BusinessException.class);
    } finally { TenantContext.clear(); }
  }
}
