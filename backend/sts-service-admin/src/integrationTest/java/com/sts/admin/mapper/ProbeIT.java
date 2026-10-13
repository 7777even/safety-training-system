package com.sts.admin.mapper;

import com.sts.admin.entity.SysOperLog;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.support.MapperItConfig;
import com.sts.admin.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest(classes = MapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ProbeIT extends AbstractMysqlIntegrationTest {
  @Autowired
  private SysOperLogMapper mapper;

  private static SysOperLog log() {
    SysOperLog l = new SysOperLog();
    l.setTitle("探针");
    l.setBusinessType(1);
    l.setMethod("m");
    l.setRequestMethod("GET");
    l.setOperatorCode("c");
    l.setOperIp("127.0.0.1");
    l.setOperUrl("/u");
    l.setStatus(1);
    return l;
  }

  @Test
  void withContext_ok_withoutContext_fails() {
    assertThat(mapper.insert(log())).isEqualTo(1);   // 有上下文（基类绑定 DEMO）
    TenantContext.clear();                            // 模拟 @Async 线程：上下文缺失
    Throwable t = catchThrowable(() -> mapper.insert(log()));
    System.out.println(">>> 无上下文插入结果: " + (t == null ? "成功(无缺陷)" : t.getClass().getSimpleName() + " / " + String.valueOf(
      t.getMessage()).lines().findFirst().orElse("")));
  }
}
