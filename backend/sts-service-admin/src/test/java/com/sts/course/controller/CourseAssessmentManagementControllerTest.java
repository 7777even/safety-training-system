package com.sts.course.controller;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageRequestArgumentResolver;
import com.sts.common.api.PageResult;
import com.sts.course.dto.CourseAssessmentResultQuery;
import com.sts.course.service.CourseAssessmentManagementService;
import com.sts.exam.vo.ExamReviewerVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CourseAssessmentManagementControllerTest {

  @Test
  void reviewers_routeDelegatesStableOfferingAndItemContext() throws Exception {
    CourseAssessmentManagementService service = mock(CourseAssessmentManagementService.class);
    when(service.listReviewers(10L, 40L)).thenReturn(List.of(new ExamReviewerVO()));
    MockMvc mvc = standaloneSetup(new CourseAssessmentManagementController(service)).build();

    mvc.perform(get("/course/offerings/10/items/40/assessment/reviewers"))
      .andExpect(status().isOk());

    verify(service).listReviewers(10L, 40L);
  }

  @Test
  void results_routeAcceptsPagingWithoutQueryParams() throws Exception {
    MethodParameter pagingParameter = new MethodParameter(
      CourseAssessmentManagementController.class.getMethod("pageResults", Long.class, Long.class, PageRequest.class), 2);
    assertEquals(CourseAssessmentResultQuery.class,
                 ResolvableType.forMethodParameter(pagingParameter).getGeneric(0).resolve());
    CourseAssessmentManagementService service = mock(CourseAssessmentManagementService.class);
    when(service.pageResults(eq(18L), eq(26L), any())).thenReturn(new PageResult<>(List.of(), 0));
    MockMvc mvc = standaloneSetup(new CourseAssessmentManagementController(service))
      .setCustomArgumentResolvers(new PageRequestArgumentResolver())
      .build();

    mvc.perform(get("/course/offerings/18/items/26/assessment/results")
                  .param("pageNum", "1")
                  .param("pageSize", "100"))
      .andExpect(status().isOk());

    ArgumentCaptor<PageRequest<CourseAssessmentResultQuery>> requestCaptor = ArgumentCaptor.forClass(PageRequest.class);
    verify(service).pageResults(eq(18L), eq(26L), requestCaptor.capture());
    PageRequest<CourseAssessmentResultQuery> request = requestCaptor.getValue();
    assertEquals(1, request.getPageNum());
    assertEquals(100, request.getPageSize());
    assertEquals(CourseAssessmentResultQuery.class, request.getQuery().getClass());
  }
}
