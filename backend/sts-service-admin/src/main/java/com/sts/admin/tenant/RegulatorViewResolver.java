package com.sts.admin.tenant;

import com.sts.common.exception.BusinessException;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.org.service.TenantDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

/**
 * 解析监管平台的「切换视角」请求头 {@code X-Target-Tenant-Id}。
 * <p>
 * 监管方查看某企业明细时不为业务接口增加分支，而是把请求的租户上下文切到目标企业，
 * 业务 Service 与 SQL 完全不变。详见 design.md §4.2。
 * <p>
 * 本类只负责「能不能切、切到哪个租户」，绑定与审计由 {@link TenantContextFilter} 完成。
 */
@Service
@RequiredArgsConstructor
public class RegulatorViewResolver {

  /**
   * 切换视角请求头。
   */
  public static final String HEADER = "X-Target-Tenant-Id";

  /**
   * 只读方法；切换视角下仅允许这些方法，监管为只读。
   */
  private static final Set<String> READ_ONLY_METHODS = Set.of("GET", "HEAD", "OPTIONS");

  private final OrganizationMapper organizationMapper;

  /**
   * 校验并解析目标租户。
   *
   * @param rawHeader     请求头原始值
   * @param callerOrgType 调用方所属租户类型；{@code superAdmin} 传 {@code null}
   * @param httpMethod    请求方法，用于强制只读
   * @return 目标企业租户 ID
   */
  public Long resolveTargetTenant(String rawHeader, String callerOrgType, String httpMethod) {
    // 非 REGULATOR 提交该头 MUST 403，且 MUST NOT 静默忽略后按自身租户执行（design §4.2）
    if (!TenantDirectory.TYPE_REGULATOR.equals(callerOrgType)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 4061,
                                  "仅监管平台租户可切换查看视角");
    }
    if (!READ_ONLY_METHODS.contains(httpMethod == null ? "" : httpMethod.toUpperCase())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 4062,
                                  "监管平台为只读，切换视角下不允许写操作");
    }

    Long targetTenantId = parse(rawHeader);
    // 旁路读取：监管租户上下文下查企业租户会被自身条件过滤掉
    Organization target = TenantContext.callWithBypass(() -> organizationMapper.selectById(targetTenantId));
    // 不存在、已停用、或根本不是企业租户，一律返回同一话术，不泄露目标租户是否存在
    if (target == null
      || !Objects.equals(target.getStatus(), 1)
      || !TenantDirectory.isRegulatedOrgType(target.getOrgType())) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 4063, "目标企业租户不存在或不可访问");
    }
    return targetTenantId;
  }

  private static Long parse(String rawHeader) {
    try {
      return Long.valueOf(rawHeader.trim());
    } catch (NumberFormatException ex) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 4064, "切换视角的目标租户 ID 非法");
    }
  }
}
