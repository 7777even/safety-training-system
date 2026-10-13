package com.sts.org.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.org.dto.OrganizationCreateRequest;
import com.sts.org.dto.OrganizationQuery;
import com.sts.org.dto.OrganizationUpdateRequest;
import com.sts.org.service.OrganizationService;
import com.sts.org.vo.OrganizationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 租户主数据管理接口。
 * <p>
 * superAdmin 可管理全部租户；监管平台租户可分类创建、查看、编辑企业和培训机构。
 * 功能项 4 的授权通过机构启用、管理员开户及初始权限完成。
 */
@RestController
@RequiredArgsConstructor
public class OrganizationController {

  private final OrganizationService organizationService;

  /**
   * 分页查询租户。
   */
  @GetMapping("/org/organizations")
  @SaCheckPermission("system:tenant:list")
  public PageResult<OrganizationVO> pageOrganizations(PageRequest<OrganizationQuery> request) {
    return organizationService.pageOrganizations(StpUtil.getLoginIdAsLong(), request);
  }

  /**
   * 查询租户详情。
   */
  @GetMapping("/org/organizations/{organizationId}")
  @SaCheckPermission("system:tenant:list")
  public OrganizationVO getOrganization(@PathVariable Long organizationId) {
    return organizationService.getOrganization(StpUtil.getLoginIdAsLong(), organizationId);
  }

  /**
   * 创建租户（企业开户）。
   */
  @PostMapping("/org/organizations")
  @SaCheckPermission("system:tenant:add")
  public Long createOrganization(@RequestBody @Valid OrganizationCreateRequest request) {
    return organizationService.createOrganization(StpUtil.getLoginIdAsLong(), request);
  }

  /**
   * 修改租户，含停用（status=0）。企业编码不可修改；企业与培训机构类型可明确改写。
   */
  @PutMapping("/org/organizations/{organizationId}")
  @SaCheckPermission("system:tenant:edit")
  public void updateOrganization(@PathVariable Long organizationId,
                                 @RequestBody @Valid OrganizationUpdateRequest request) {
    organizationService.updateOrganization(StpUtil.getLoginIdAsLong(), organizationId, request);
  }
}
