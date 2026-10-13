package com.sts.exam.service;

import com.sts.exam.mapper.*;
import com.sts.exam.vo.ExamVO;
import com.sts.admin.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;

/** 无执行、资源不可见与只读边界。 */
class ExamStatisticsServiceTest {
  @Test void maximumLegalPageSizeDoesNotOverflowAndPeopleDoNotLoadAnswers() {
    var exam=mock(ExamService.class);var x=mock(AssessmentExecutionMapper.class);
    when(exam.get(1L)).thenReturn(new ExamVO());when(x.selectList(any())).thenReturn(java.util.List.of());
    var questions=mock(ExamAttemptQuestionMapper.class);var answers=mock(ExamAnswerMapper.class);
    var service=spy(new ExamStatisticsService(exam,x,mock(ExamCandidateMapper.class),mock(ExamAttemptMapper.class),questions,answers,mock(SysUserMapper.class),new ObjectMapper()));
    var row=new com.sts.exam.vo.ExamStatisticsPersonVO(1L,1L,"张三","u1","PASSED",null,null,false,false,false,null,null,null,null,null,null);
    doReturn(java.util.List.of(row)).when(service).people(1L,null);
    assertThat(service.page(1L,new com.sts.common.api.PageRequest<>(2,Integer.MAX_VALUE,null)).list()).isEmpty();
    verifyNoInteractions(questions,answers);
  }
  @Test void emptyExamDoesNotCreateExecutionOrReadUnboundedFacts() {
    var exam=mock(ExamService.class); var x=mock(AssessmentExecutionMapper.class);
    var candidates=mock(ExamCandidateMapper.class); var attempts=mock(ExamAttemptMapper.class);
    var questions=mock(ExamAttemptQuestionMapper.class); var answers=mock(ExamAnswerMapper.class); var users=mock(SysUserMapper.class);
    when(exam.get(1L)).thenReturn(new ExamVO()); when(x.selectList(any())).thenReturn(java.util.List.of());
    var service=new ExamStatisticsService(exam,x,candidates,attempts,questions,answers,users,new ObjectMapper());
    assertThat(service.statistics(1L).expectedCount()).isZero();
    verifyNoInteractions(candidates,attempts,questions,answers,users);
    verify(exam).get(1L);
  }
}
