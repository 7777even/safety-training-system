package com.sts.person.controller;

import cn.dev33.satoken.exception.NotPermissionException;
import com.sts.common.api.PageRequestArgumentResolver;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.person.service.PersonPickerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PersonPickerControllerWebSliceTest {

  private PersonPickerService personPickerService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    personPickerService = mock(PersonPickerService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new PersonPickerController(personPickerService))
      .setCustomArgumentResolvers(new PageRequestArgumentResolver())
      .setControllerAdvice(new HttpApiExceptionHandler())
      .build();
  }

  @Test
  @DisplayName("人员选择权限不足返回 403 和业务码 1003")
  void notPermissionException_maps403() throws Exception {
    when(personPickerService.pagePersons(any()))
      .thenThrow(new NotPermissionException("common:person:list"));

    mockMvc.perform(get("/common/person-picker")
                      .param("pageNum", "1")
                      .param("pageSize", "20"))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value(1003));
  }
}
