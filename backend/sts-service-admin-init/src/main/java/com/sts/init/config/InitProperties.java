package com.sts.init.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sts.init")
public class InitProperties {
    private boolean enabled = true;

  /**
   * 当前模块化完整 SQL 对应的 Flyway 基线版本。
   */
  private String schemaVersion = "20260910195000";

    /**
     * 根部门名称 (如: 发展管委会)
     */
    private String rootDeptName = "发展管委会";
}
