package com.sts.admin.system.controller;

import com.sts.admin.system.dto.MenuBatchVisibilityRequest;
import com.sts.admin.system.dto.MenuCreateRequest;
import com.sts.admin.system.dto.MenuUpdateRequest;
import com.sts.admin.system.service.SysMenuService;
import com.sts.admin.system.vo.MenuTreeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SysMenuController} Web 层测试。
 * standaloneSetup + mock service，断言查询参数透传、路径 id、以及 {@code @Valid} 校验。
 */
class SysMenuControllerTest {

    private SysMenuService menuService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        menuService = mock(SysMenuService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SysMenuController(menuService)).build();
    }

    @Test
    @DisplayName("GET /system/menus/tree：三个查询参数原样透传，返回树")
    void getMenuTree_passesQueryParams() throws Exception {
        MenuTreeVO node = new MenuTreeVO();
        node.setMenuId(1L);
        node.setMenuName("系统管理");
        when(menuService.getMenuTree(any(), any(), any())).thenReturn(List.of(node));

        mockMvc.perform(get("/system/menus/tree")
                            .param("menuName", "系统")
                            .param("perms", "sys:user")
                            .param("isVisible", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].menuId").value(1))
            .andExpect(jsonPath("$[0].menuName").value("系统管理"));

        verify(menuService).getMenuTree("系统", "sys:user", 1);
    }

    @Test
    @DisplayName("GET /system/menus/tree：不传参数时三个入参均为 null")
    void getMenuTree_absentParamsAreNull() throws Exception {
        when(menuService.getMenuTree(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/system/menus/tree"))
            .andExpect(status().isOk());

        verify(menuService).getMenuTree(null, null, null);
    }

  @Test
  @DisplayName("POST /system/menus：合法请求委派并返回菜单 ID")
  void createMenu_valid_delegatesAndReturnsId() throws Exception {
    when(menuService.createMenu(any(MenuCreateRequest.class))).thenReturn(9L);
    String body = "{\"parentId\":0,\"menuName\":\"系统管理\",\"isVisible\":1,\"menuType\":\"M\"}";

    mockMvc.perform(post("/system/menus").contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$").value(9));

    verify(menuService).createMenu(any(MenuCreateRequest.class));
  }

  @Test
  @DisplayName("PUT /system/menus/{id}：路径 ID 与请求体透传")
  void updateMenu_valid_delegates() throws Exception {
    String body = "{\"parentId\":0,\"menuName\":\"系统管理\",\"isVisible\":1,\"menuType\":\"M\",\"version\":2}";

    mockMvc.perform(put("/system/menus/9").contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isOk());

    verify(menuService).updateMenu(eq(9L), any(MenuUpdateRequest.class));
  }

  @Test
  @DisplayName("DELETE /system/menus/{id}：路径 ID 透传")
  void deleteMenu_valid_delegates() throws Exception {
    mockMvc.perform(delete("/system/menus/9"))
      .andExpect(status().isOk());

    verify(menuService).deleteMenu(9L);
  }

    @Test
    @DisplayName("PUT /system/menus/{id}/visibility：路径 id + 请求体透传")
    void updateVisibility_valid_delegates() throws Exception {
        String body = "{\"isVisible\":0,\"version\":2}";

        mockMvc.perform(put("/system/menus/5/visibility")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
            .andExpect(status().isOk());

        verify(menuService).updateVisibility(5L, 0, 2);
    }

    @Test
    @DisplayName("PUT /system/menus/{id}/visibility：缺少 version → 400，且不进 service")
    void updateVisibility_missingVersion_badRequest() throws Exception {
        String body = "{\"isVisible\":0}";

        mockMvc.perform(put("/system/menus/5/visibility")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
            .andExpect(status().isBadRequest());

        verify(menuService, never()).updateVisibility(any(), any(), any());
    }

    @Test
    @DisplayName("PUT /system/menus/batch-visibility：可见集合与版本项透传")
    void batchUpdateVisibility_valid_delegates() throws Exception {
        String body = "{\"visibleIds\":[10,11],"
            + "\"items\":[{\"menuId\":10,\"version\":1},{\"menuId\":11,\"version\":1}]}";

        mockMvc.perform(put("/system/menus/batch-visibility")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
            .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MenuBatchVisibilityRequest.MenuVersionItem>> itemsCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(menuService).batchUpdateVisibility(idsCaptor.capture(), itemsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(10L, 11L);
        assertThat(itemsCaptor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("PUT /system/menus/batch-visibility：visibleIds 为空 → 400，且不进 service")
    void batchUpdateVisibility_emptyVisibleIds_badRequest() throws Exception {
        String body = "{\"visibleIds\":[],\"items\":[{\"menuId\":10,\"version\":1}]}";

        mockMvc.perform(put("/system/menus/batch-visibility")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
            .andExpect(status().isBadRequest());

        verify(menuService, never()).batchUpdateVisibility(any(), any());
    }
}
