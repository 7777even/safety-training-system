package com.sts.admin.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysLoginLog;
import com.sts.admin.entity.SysOperLog;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.support.MapperItConfig;
import com.sts.admin.tenant.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 两条异步日志管道的租户归属（Testcontainers 真 MySQL）。
 * <p>
 * 日志经 {@code @Async} 线程落库，租户 ThreadLocal 不跨线程，租户插件不会为 INSERT 注入
 * {@code organization_id}；而该列是 NOT NULL，于是插入必然失败并被监听器的 catch 吞掉。
 * 修复方式是在请求线程内取值、显式写进实体。本用例固化这条约束——
 * 一旦有人把 {@code organizationId} 的显式赋值去掉，审计与操作日志会再次静默丢失。
 */
@SpringBootTest(classes = MapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class AsyncLogTenantIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private SysOperLogMapper operLogMapper;
  @Autowired
  private SysLoginLogMapper loginLogMapper;

  private static SysOperLog log(String title, Long organizationId) {
    SysOperLog log = new SysOperLog();
    log.setOrganizationId(organizationId);
    log.setTitle(title);
    log.setBusinessType(0);
    log.setMethod("com.sts.Demo.method");
    log.setRequestMethod("GET");
    log.setOperatorCode("tester");
    log.setOperIp("127.0.0.1");
    log.setOperUrl("/api/demo");
    log.setStatus(1);
    return log;
  }

  @Test
  @DisplayName("实体显式携带租户后，无上下文的异步线程也能成功落库")
  void insert_succeedsWithoutTenantContextWhenOrganizationIdIsSet() {
    Long demo = demoTenantId();
    TenantContext.clear(); // 模拟 @Async 线程：上下文缺失

    assertThat(operLogMapper.insert(log("异步写入", demo)))
      .describedAs("显式赋值后不应再违反 organization_id 非空约束").isEqualTo(1);
  }

  @Test
  @DisplayName("平台级操作记 0：superAdmin 旁路时无租户归属")
  void platformLevelOperation_isRecordedWithZeroTenant() {
    TenantContext.clear();

    assertThat(operLogMapper.insert(log("平台级操作", 0L))).isEqualTo(1);
  }

  @Test
  @DisplayName("操作日志按租户隔离：查询只返回本租户日志")
  void select_isTenantScoped() {
    Long demo = demoTenantId();
    operLogMapper.insert(log("本租户日志", demo));
    TenantContext.callWithBypass(() -> operLogMapper.insert(log("别家日志", demo + 9999)));

    assertThat(operLogMapper.selectList(new LambdaQueryWrapper<SysOperLog>()
                                          .in(SysOperLog::getTitle, "本租户日志", "别家日志")))
      .extracting(SysOperLog::getTitle)
      .containsExactly("本租户日志");
  }

  private static SysLoginLog loginLog(String userCode, Long organizationId) {
    SysLoginLog log = new SysLoginLog();
    log.setOrganizationId(organizationId);
    log.setUserCode(userCode);
    log.setIpaddr("127.0.0.1");
    log.setLoginLocation("Local");
    log.setBrowser("JUnit");
    log.setOs("Linux");
    log.setStatus(1);
    log.setMsg("登录成功");
    log.setLoginTime(java.time.LocalDateTime.now());
    return log;
  }

  @Test
  @DisplayName("登录日志同样在无上下文的异步线程中落库")
  void loginLog_insertSucceedsWithoutTenantContext() {
    Long demo = demoTenantId();
    TenantContext.clear();

    assertThat(loginLogMapper.insert(loginLog("tester", demo))).isEqualTo(1);
  }

  /**
   * 登录失败时租户可能根本解析不出来（企业编码不存在、渠道禁用），
   * 此时按约定记 0，MUST NOT 因为非空约束把登录审计整条丢掉。
   */
  @Test
  @DisplayName("租户无法解析的失败登录记 0，不丢审计")
  void loginLog_unresolvedTenantIsRecordedAsZero() {
    TenantContext.clear();

    assertThat(loginLogMapper.insert(loginLog("ghost", 0L))).isEqualTo(1);
  }
}
