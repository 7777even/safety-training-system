package com.sts.admin.tenant;

/**
 * 登录会话中承载租户身份的 Session Key。
 * <p>
 * 登录成功时写入，{@link TenantContextFilter} 在每个请求开始时读取并绑定租户上下文。
 */
public final class TenantSessionKeys {

  /**
   * 当前用户所属租户 ID。
   */
  public static final String ORGANIZATION_ID = "tenant:organizationId";

  /**
   * 当前用户所属租户类型：ENTERPRISE / TRAINING_INSTITUTION / REGULATOR。
   */
  public static final String ORG_TYPE = "tenant:orgType";

  /**
   * 是否为 superAdmin；只用于功能权限和平台身份判定，不表示租户旁路。
   */
  public static final String SUPER_ADMIN = "tenant:superAdmin";

  private TenantSessionKeys() {
  }
}
