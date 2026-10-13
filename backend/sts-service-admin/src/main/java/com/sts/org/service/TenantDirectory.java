package com.sts.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 租户目录查询。
 * <p>
 * 监管平台无条件覆盖全部启用中的企业和培训机构租户，不依赖监管关系配置。
 */
@Service
@RequiredArgsConstructor
public class TenantDirectory {

  public static final String TYPE_ENTERPRISE = "ENTERPRISE";
  public static final String TYPE_TRAINING_INSTITUTION = "TRAINING_INSTITUTION";
  public static final String TYPE_REGULATOR = "REGULATOR";
  public static final Set<String> REGULATED_TYPES = Set.of(TYPE_ENTERPRISE, TYPE_TRAINING_INSTITUTION);

  private final OrganizationMapper organizationMapper;

  /**
   * 是否为受监管业务租户类型（企业或培训机构）。
   */
  public static boolean isRegulatedOrgType(String orgType) {
    return REGULATED_TYPES.contains(orgType);
  }

  /**
   * 列出全部启用中的受监管租户 ID。
   */
  public List<Long> listRegulatedTenantIds() {
    return organizationMapper.selectList(
        new LambdaQueryWrapper<Organization>()
          .in(Organization::getOrgType, REGULATED_TYPES)
          .eq(Organization::getStatus, 1))
      .stream()
      .map(Organization::getOrganizationId)
      .distinct()
      .toList();
  }

  /**
   * 列出全部启用中的企业租户 ID。
   *
   * @deprecated 监管范围已含培训机构，请改用 {@link #listRegulatedTenantIds()}
   */
  @Deprecated
  public List<Long> listEnterpriseTenantIds() {
    return listRegulatedTenantIds();
  }

  /**
   * 全系统唯一监管平台租户；不存在时返回 null。
   */
  public Long findRegulatorOrganizationId() {
    Organization regulator = organizationMapper.selectOne(
      new LambdaQueryWrapper<Organization>()
        .eq(Organization::getOrgType, TYPE_REGULATOR)
        .eq(Organization::getStatus, 1)
        .last("LIMIT 1"));
    return regulator == null ? null : regulator.getOrganizationId();
  }
}
