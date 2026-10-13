package com.sts.training.controller;

import com.sts.common.api.PageRequestArgumentResolver;
import com.sts.training.service.TrainingReferenceResourceService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrainingReferenceResourceControllerTest {

  @Test
  void page_bindsQueryAndDelegatesWithoutPassingIdentity() throws Exception {
    TrainingReferenceResourceService service = mock(TrainingReferenceResourceService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new TrainingReferenceResourceController(service))
      .setCustomArgumentResolvers(new PageRequestArgumentResolver())
      .build();

    mvc.perform(get("/training/reference-resources")
                  .param("pageNum", "2")
                  .param("pageSize", "15")
                  .param("query.resourceType", "COURSE")
                  .param("query.keyword", "安全"))
      .andExpect(status().isOk());

    verify(service).page(argThat(request -> request.getPageNum() == 2
      && request.getPageSize() == 15
      && "COURSE".equals(request.getQuery().getResourceType())
      && "安全".equals(request.getQuery().getKeyword())));
  }
}
