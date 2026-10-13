package com.sts.admin.tenant;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.aop.event.OperLogEvent;
import com.sts.admin.entity.SysOperLog;
import com.sts.admin.system.service.SysUserOnlineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 监管平台跨租户访问审计。
 * <p>
 * design §4.2 要求切换视角的每次请求都记录监管人、目标租户与访问资源。
 * 复用既有操作日志管道（{@code us_sys_oper_log}），日志本身归属监管租户，
 * 这样监管方的访问轨迹留在监管租户内，被查企业看不到，也不会污染企业自己的操作日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegulatorAccessAuditor {

  /**
   * 审计标题，便于按标题检索全部跨租户访问。
   */
  public static final String TITLE = "监管跨租户访问";

  /**
   * 业务类型：其它/查询。
   */
  private static final int BUSINESS_TYPE_QUERY = 0;

  private final ApplicationEventPublisher eventPublisher;

  /**
   * @param regulatorOrgId 监管租户 ID，审计日志归属该租户
   * @param targetTenantId 被查看的企业租户 ID
   * @param requestMethod  HTTP 方法
   * @param requestUri     被访问的资源 URI
   * @param clientIp       来源 IP
   */
  public void record(Long regulatorOrgId, Long targetTenantId,
                     String requestMethod, String requestUri, String clientIp) {
    try {
      SysOperLog auditLog = new SysOperLog();
      auditLog.setOrganizationId(regulatorOrgId);
      auditLog.setTitle(TITLE);
      auditLog.setBusinessType(BUSINESS_TYPE_QUERY);
      auditLog.setMethod(RegulatorViewResolver.HEADER + "=" + targetTenantId);
      auditLog.setRequestMethod(requestMethod);
      auditLog.setOperUrl(requestUri);
      auditLog.setOperIp(clientIp);
      auditLog.setOperParam("{\"targetTenantId\":" + targetTenantId + "}");
      auditLog.setStatus(1);
      if (StpUtil.isLogin()) {
        auditLog.setOperatorCode(
          StpUtil.getTokenSession().getString(SysUserOnlineService.SESSION_KEY_USER_CODE));
      }
      eventPublisher.publishEvent(new OperLogEvent(this, auditLog));
    } catch (Exception ex) {
      // 审计失败不阻断业务请求，但必须留痕以便排查
      log.error("监管跨租户访问审计写入失败 targetTenantId={}", targetTenantId, ex);
    }
  }
}
