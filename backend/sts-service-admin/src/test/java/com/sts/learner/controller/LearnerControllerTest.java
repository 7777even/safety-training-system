package com.sts.learner.controller;

import com.sts.common.api.*;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.learner.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;

/** HTTP分页/分类参数绑定；登录与归属另由安全与领域测试覆盖。 */
class LearnerControllerTest {
  @Test void homeBindsKindAndKeywordAndRejectsInvalidPage() throws Exception {
    var home = mock(LearnerHomeService.class);
    when(home.tasks(any())).thenReturn(new PageResult<>(List.of(), 0));
    var mvc = MockMvcBuilders.standaloneSetup(new LearnerController(home, mock(LearnerMessageService.class), mock(LearnerMessageStream.class)))
      .setCustomArgumentResolvers(new PageRequestArgumentResolver()).setControllerAdvice(new HttpApiExceptionHandler()).build();
    mvc.perform(get("/learner/home/tasks").param("pageNum", "2").param("pageSize", "20")
      .param("query.kind", "COURSE").param("query.keyword", "安全")).andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
    var captor = org.mockito.ArgumentCaptor.forClass(PageRequest.class);
    verify(home).tasks(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageNum()).isEqualTo(2);
    org.assertj.core.api.Assertions.assertThat(((LearnerHomeService.Query) captor.getValue().getQuery()).getKeyword()).isEqualTo("安全");
    mvc.perform(get("/learner/home/tasks").param("pageNum", "0")).andExpect(status().isBadRequest());
    mvc.perform(get("/learner/messages").param("pageSize", "201")).andExpect(status().isBadRequest());
    verify(home, times(1)).tasks(any());
  }
}
