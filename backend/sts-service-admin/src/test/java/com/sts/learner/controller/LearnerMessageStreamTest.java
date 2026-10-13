package com.sts.learner.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.tenant.TenantContext;
import com.sts.learner.mapper.LearnerMessageMapper;
import com.sts.learner.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/** 通过真实MVC异步响应验证推送、心跳、重连及已撤销会话，不读取连接内部状态。 */
class LearnerMessageStreamTest {
  @Test void streamPushesCommittedCountsAndReconnectsWithoutTrustingClientIdentity() throws Exception {
    var mapper = mock(LearnerMessageMapper.class);
    when(mapper.counts(1L, List.of(7L))).thenReturn(List.of(new LearnerMessageMapper.Count(7L, 1L)));
    var stream = new LearnerMessageStream(mapper);
    var mvc = MockMvcBuilders.standaloneSetup(new LearnerController(mock(LearnerHomeService.class), mock(LearnerMessageService.class), stream)).build();
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L); stp.when(StpUtil::getTokenValue).thenReturn("test-token");
      stp.when(() -> StpUtil.getLoginIdByToken("test-token")).thenReturn("7");
      TenantContext.setTenantId(1L);
      var first = mvc.perform(get("/learner/messages/stream")).andExpect(request().asyncStarted()).andReturn();
      stream.push(); assertThat(first.getResponse().getContentAsString()).contains("event:messages", "\"total\":1");
      stream.push(); assertThat(first.getResponse().getContentAsString()).contains(":heartbeat");
      var reconnect = mvc.perform(get("/learner/messages/stream")).andExpect(request().asyncStarted()).andReturn();
      stream.push(); assertThat(reconnect.getResponse().getContentAsString()).contains("event:messages");
      stp.when(() -> StpUtil.getLoginIdByToken("test-token")).thenReturn(null);
      stream.push(); clearInvocations(mapper); stream.push(); verifyNoInteractions(mapper);
    } finally { TenantContext.clear(); }
  }
}
