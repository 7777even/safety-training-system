package com.sts.admin.system.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.admin.system.service.SysUserService;
import com.sts.admin.system.vo.UserVO;
import com.sts.common.api.PageRequestArgumentResolver;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.exception.HttpApiExceptionHandler;
import io.github.easytrans.core.spi.TranslationExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SysUserController} Web 切片测试 —— 补 {@link SysUserControllerTest} 明确留白的「鉴权 + Result 包装」那一层。
 * <p>
 * 与 {@code SysUserControllerTest}（standaloneSetup 断言<b>原始返回体</b>）不同，这里额外挂上**真实的**：
 * <ul>
 *   <li>{@link ResultEasyTransAdvice} —— 把 Controller 裸返回统一包成 {@code Result}（{@code code=0}）；
 *       {@link TranslationExecutor} 用 no-op mock（翻译非本切片关注点）。</li>
 *   <li>{@link HttpApiExceptionHandler} —— 把异常映射成**真实 HTTP 4xx/5xx + {@code Result.fail}**。</li>
 * </ul>
 * 于是可验证响应「外壳契约」：成功包装、失败必带真实状态码（绝不 200 装失败）、鉴权异常→401/403、序列化脱敏。
 * <p>
 * 注：{@code @SaCheckPermission} 注解的<b>拦截触发</b>是 Sa-Token 自身职责（其已自测），这里验证的是**我们的**
 * {@link HttpApiExceptionHandler} 把 {@link NotLoginException}/{@link NotPermissionException} 映射到 401/403 + 业务码。
 */
class SysUserControllerWebSliceTest {

  private static final String PASSWORD_DIGEST = "a".repeat(64);

    private SysUserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(SysUserService.class);
        TranslationExecutor translationExecutor = mock(TranslationExecutor.class); // no-op 翻译
        mockMvc = MockMvcBuilders.standaloneSetup(new SysUserController(userService))
            .setCustomArgumentResolvers(new PageRequestArgumentResolver())
            .setControllerAdvice(
                new HttpApiExceptionHandler(),
                new ResultEasyTransAdvice(translationExecutor, new ObjectMapper()))
            .build();
    }

    // ---------- Result 统一包装 ----------

    @Test
    @DisplayName("裸对象返回被包成 Result：code=0 / message=success / data 为真实对象")
    void rawObject_isWrappedIntoResult() throws Exception {
        UserVO vo = new UserVO();
        vo.setUserId(1L);
        vo.setUserCode("superAdmin");
        when(userService.getUserProfile()).thenReturn(vo);

        mockMvc.perform(get("/system/users/profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.message").value("success"))
            .andExpect(jsonPath("$.data.userId").value(1))
            .andExpect(jsonPath("$.data.userCode").value("superAdmin"));
    }

    @Test
    @DisplayName("PageResult 返回同样被包进 Result.data（code=0 + data.total/list）")
    void pageResult_isWrapped() throws Exception {
        UserVO vo = new UserVO();
        vo.setUserId(2L);
        vo.setUserCode("admin");
        when(userService.pageUsers(any())).thenReturn(new PageResult<>(List.of(vo), 1L));

        mockMvc.perform(get("/system/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list[0].userCode").value("admin"));
    }

    // ---------- 失败必带真实 HTTP 状态码（铁律：绝不 200 装失败） ----------

    @Test
    @DisplayName("Service 抛 BusinessException(409) → 响应真为 409，且 body 是 Result.fail(业务码)")
    void businessException_yieldsRealHttpStatus_notWrapped200() throws Exception {
        doThrow(new BusinessException(HttpStatus.CONFLICT, 3001, "设备被引用，无法删除"))
            .when(userService).deleteUser(9L);

        mockMvc.perform(delete("/system/users/9"))
            .andExpect(status().isConflict())                 // 真实 409，不是 200
            .andExpect(jsonPath("$.code").value(3001))
            .andExpect(jsonPath("$.message").value("设备被引用，无法删除"))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("@Valid 失败 → 400 且 body 为 Result.fail(1001)，不进 service")
    void validationFailure_returns400WithCode1001() throws Exception {
      String invalid = "{\"userCode\":\"\",\"userName\":\"新用户\",\"deptId\":10,\"password\":\""
        + PASSWORD_DIGEST + "\"}";

        mockMvc.perform(post("/system/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalid))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(1001));

        verify(userService, never()).createUser(any());
    }

  @Test
  @DisplayName("不可见 Unicode 校验失败 → 400 / code=1001，且不进 service")
  void invisibleUnicode_returns400WithCode1001() throws Exception {
    String invalid = "{\"userCode\":\"newuser\",\"userName\":\"新\\u200B用户\","
      + "\"deptId\":10,\"password\":\"" + PASSWORD_DIGEST + "\"}";

    mockMvc.perform(post("/system/users")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(invalid))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value(1001));

    verify(userService, never()).createUser(any());
  }

  @Test
  @DisplayName("更新资料含不可见 Unicode → 400 / code=1001，且不进 service")
  void updateInvisibleUnicode_returns400WithCode1001() throws Exception {
    String invalid = "{\"userName\":\"新用户\",\"jobTitle\":\"安全\\u2060员\","
      + "\"deptId\":10,\"status\":1,\"version\":0}";

    mockMvc.perform(put("/system/users/7")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(invalid))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value(1001));

    verify(userService, never()).updateUser(any(), any());
  }

  @Test
  @DisplayName("重置密码含不可见 Unicode → 400 / code=1001，且不进 service")
  void resetPasswordInvisibleUnicode_returns400WithCode1001() throws Exception {
    String invalid = "{\"password\":\"" + PASSWORD_DIGEST + "\\uFEFF\"}";

    mockMvc.perform(put("/system/users/7/reset-password")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(invalid))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value(1001));

    verify(userService, never()).resetPassword(any(), any());
  }

    // ---------- 鉴权异常映射（HttpApiExceptionHandler） ----------

    @Test
    @DisplayName("未登录异常 → 401 / code=1002")
    void notLoginException_maps401() throws Exception {
        when(userService.getUserProfile()).thenThrow(
            NotLoginException.newInstance("login", NotLoginException.NOT_TOKEN,
                                          NotLoginException.NOT_TOKEN_MESSAGE, null));

        mockMvc.perform(get("/system/users/profile"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    @DisplayName("权限不足异常 → 403 / code=1003")
    void notPermissionException_maps403() throws Exception {
        doThrow(new NotPermissionException("sys:user:delete"))
            .when(userService).deleteUser(9L);

        mockMvc.perform(delete("/system/users/9"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(1003));
    }

    // ---------- 序列化脱敏 ----------

    @Test
    @DisplayName("@Sensitive(PHONE)：手机号在 JSON 输出中被脱敏，不回传明文")
    void phone_isMaskedInSerialization() throws Exception {
        UserVO vo = new UserVO();
        vo.setUserId(1L);
        vo.setUserCode("admin");
        vo.setPhone("13800001111");
        when(userService.getUserProfile()).thenReturn(vo);

        mockMvc.perform(get("/system/users/profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.phone").value(org.hamcrest.Matchers.containsString("*")))
            .andExpect(jsonPath("$.data.phone").value(org.hamcrest.Matchers.not("13800001111")));
    }
}
