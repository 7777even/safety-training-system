package com.sts.question.controller;

import com.sts.question.service.LearnerPracticeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

/** HTTP信任边界：错误枚举、空集合及空收藏状态不进入业务服务。 */
class LearnerPracticeControllerTest {
  /** 收藏分页只序列化安全摘要，不附带原模拟答案或解析。 */
  @Test void favoriteListOnlyReturnsSafeSummary() throws Exception {
    var service = mock(LearnerPracticeService.class);
    var favorite = new com.sts.question.dto.LearnerPracticeModels.FavoriteQuestion(5L, 8L, "SINGLE", "安全题干", "BANK_PRACTICE", "安全题库", 1L, true);
    when(service.favorites(any())).thenReturn(new com.sts.common.api.PageResult<>(java.util.List.of(favorite), 1));
    var mvc = MockMvcBuilders.standaloneSetup(new LearnerPracticeController(service)).build();
    mvc.perform(get("/question/learner/favorite-questions").param("pageNum", "1").param("pageSize", "20"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.list[0].questionId").value(5))
      .andExpect(jsonPath("$.list[0].correctAnswerJson").doesNotExist())
      .andExpect(jsonPath("$.list[0].analysis").doesNotExist());
  }
  @Test void invalidRequestDoesNotReachService() throws Exception {
    var service = mock(LearnerPracticeService.class);
    var mvc = MockMvcBuilders.standaloneSetup(new LearnerPracticeController(service)).build();
    mvc.perform(post("/question/learner/sessions").contentType(MediaType.APPLICATION_JSON)
      .content("{\"candidateId\":1,\"mode\":\"FORMAL\",\"clientToken\":\"token\"}")).andExpect(status().isBadRequest());
    mvc.perform(put("/question/learner/sessions/1/items/2/answer").contentType(MediaType.APPLICATION_JSON)
      .content("{\"selected\":null,\"blanks\":{}}")).andExpect(status().isBadRequest());
    mvc.perform(put("/question/learner/questions/1/favorite").contentType(MediaType.APPLICATION_JSON)
      .content("{\"favorite\":null}")).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }
}
