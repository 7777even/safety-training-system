package com.sts.org.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.org.dto.OrganizationCreateRequest;
import com.sts.org.dto.OrganizationQuery;
import com.sts.org.dto.OrganizationUpdateRequest;
import com.sts.org.vo.OrganizationVO;

/**
 * 租户主数据管理。写操作仅 superAdmin 可执行。
 */
public interface OrganizationService {
  PageResult<OrganizationVO> pageOrganizations(Long userId, PageRequest<OrganizationQuery> request);

  OrganizationVO getOrganization(Long userId, Long organizationId);

  Long createOrganization(Long userId, OrganizationCreateRequest request);

  void updateOrganization(Long userId, Long organizationId, OrganizationUpdateRequest request);
}
