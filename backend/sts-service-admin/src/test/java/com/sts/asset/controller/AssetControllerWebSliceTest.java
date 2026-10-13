package com.sts.asset.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.asset.dto.AssetCreateRequest;
import com.sts.asset.service.AssetService;
import com.sts.asset.vo.AssetListItemVO;
import com.sts.asset.vo.AssetVO;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssetControllerWebSliceTest {

  private AssetService assetService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    assetService = mock(AssetService.class);
    TranslationExecutor translationExecutor = mock(TranslationExecutor.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new AssetController(assetService))
      .setCustomArgumentResolvers(new PageRequestArgumentResolver())
      .setControllerAdvice(
        new HttpApiExceptionHandler(),
        new ResultEasyTransAdvice(translationExecutor, new ObjectMapper()))
      .build();
  }

  @Test
  @DisplayName("pageAssets PageResult 被 Result 包装")
  void pageAssets_isWrapped() throws Exception {
    AssetListItemVO vo = new AssetListItemVO();
    vo.setAssetId(100L);
    vo.setAssetCode("DOC-001");
    when(assetService.pageAssets(any())).thenReturn(new PageResult<>(List.of(vo), 1L));

    mockMvc.perform(get("/asset/assets"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.list[0].assetCode").value("DOC-001"));
  }

  @Test
  @DisplayName("getAsset 详情被 Result 包装")
  void getAsset_isWrapped() throws Exception {
    AssetVO vo = new AssetVO();
    vo.setAssetId(100L);
    vo.setAssetName("安全手册");
    when(assetService.getAsset(100L)).thenReturn(vo);

    mockMvc.perform(get("/asset/assets/100"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data.assetName").value("安全手册"));
  }

  @Test
  @DisplayName("createAsset 业务异常 → 409")
  void createAsset_businessException_yields409() throws Exception {
    when(assetService.createAsset(any(AssetCreateRequest.class)))
      .thenThrow(new BusinessException(HttpStatus.CONFLICT, 6010, "素材编码已存在"));

    mockMvc.perform(post("/asset/assets")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("""
                                 {"assetCode":"DOC-001","assetName":"安全手册","assetType":"DOCUMENT",
                                 "sourceType":"UPLOAD","fileInfoId":200}
                                 """))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(6010));
  }
}
