package com.sts.training.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.training.dto.TrainingReferenceResourceQuery;
import com.sts.training.mapper.TrainingReferenceResourceMapper;
import com.sts.training.vo.TrainingReferenceResourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class TrainingReferenceResourceServiceImpl implements TrainingReferenceResourceService {
  private static final Set<String> RESOURCE_TYPES = Set.of("COURSE", "EXAM", "QUESTION_BANK");

  private final TrainingReferenceResourceMapper resourceMapper;

  /**
   * 可见范围由租户插件在 SQL 层注入。
   * <p>
   * 此前这里按 {@code userId} 反查主部门与租户归属，再把机构 ID 拼进手写 SQL——
   * 那是隔离建立之前的补偿写法，与插件注入的条件重复，属于第二套过滤逻辑，已删除。
   */
  @Override
  public PageResult<TrainingReferenceResourceVO> page(PageRequest<TrainingReferenceResourceQuery> request) {
    TrainingReferenceResourceQuery query = request.getQuery();
    String resourceType = query == null ? null : query.getResourceType();
    if (!RESOURCE_TYPES.contains(resourceType)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 1001, "资源类型仅支持 COURSE、EXAM、QUESTION_BANK");
    }

    String keyword = query == null || !StringUtils.hasText(query.getKeyword())
      ? null : query.getKeyword().trim();
    IPage<TrainingReferenceResourceVO> page = resourceMapper.selectReferenceResourcePage(
      new Page<>(request.getPageNum(), request.getPageSize()), resourceType, keyword);
    return new PageResult<>(page.getRecords(), page.getTotal());
  }
}
