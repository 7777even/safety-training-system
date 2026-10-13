package com.sts.training.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.exception.BusinessException;
import com.sts.training.dto.TrainingReferenceResourceQuery;
import com.sts.training.mapper.TrainingReferenceResourceMapper;
import com.sts.training.vo.TrainingReferenceResourceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TrainingReferenceResourceServiceImplTest {

  private TrainingReferenceResourceMapper resourceMapper;
  private TrainingReferenceResourceServiceImpl service;

  @BeforeEach
  void setUp() {
    resourceMapper = mock(TrainingReferenceResourceMapper.class);
    service = new TrainingReferenceResourceServiceImpl(resourceMapper);
  }

  /**
   * 可见范围改由租户插件在 SQL 层注入：Service 不再按用户反查机构，
   * 也不再把机构 ID 传给 Mapper，只透传分页与查询条件。
   */
  @Test
  void page_delegatesVisibilityToTenantPluginWithoutResolvingOrganization() {
    when(resourceMapper.selectReferenceResourcePage(any(), eq("COURSE"), eq("安全")))
      .thenAnswer(invocation -> {
        Page<TrainingReferenceResourceVO> page = invocation.getArgument(0);
        TrainingReferenceResourceVO item = new TrainingReferenceResourceVO();
        item.setResourceId(9L);
        page.setRecords(java.util.List.of(item));
        page.setTotal(1);
        return page;
      });

    var result = service.page(request("COURSE", "安全"));

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.list()).extracting(TrainingReferenceResourceVO::getResourceId).containsExactly(9L);
    verify(resourceMapper).selectReferenceResourcePage(any(), eq("COURSE"), eq("安全"));
    verifyNoMoreInteractions(resourceMapper);
  }

  @Test
  void page_rejectsUnsupportedResourceTypeBeforeQueryingDatabase() {
    assertThatThrownBy(() -> service.page(request("PAPER", null)))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 1001);
    verifyNoInteractions(resourceMapper);
  }

  @Test
  void page_trimsBlankKeywordToNull() {
    when(resourceMapper.selectReferenceResourcePage(any(), eq("EXAM"), eq(null)))
      .thenAnswer(invocation -> invocation.getArgument(0));

    service.page(request("EXAM", "   "));

    verify(resourceMapper).selectReferenceResourcePage(any(), eq("EXAM"), eq(null));
  }

  private PageRequest<TrainingReferenceResourceQuery> request(String type, String keyword) {
    TrainingReferenceResourceQuery query = new TrainingReferenceResourceQuery();
    query.setResourceType(type);
    query.setKeyword(keyword);
    PageRequest<TrainingReferenceResourceQuery> request = new PageRequest<>();
    request.setPageNum(1);
    request.setPageSize(20);
    request.setQuery(query);
    return request;
  }
}
