package com.sts.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/** 开发启动必须沿用 test 的迁移行为，防止业务接口先于数据库结构上线。 */
class DevelopmentMigrationConfigurationTest {
  @Test
  void allDevelopmentProfilesEnableValidatedMigrationWithoutAutomaticBaseline() {
    for (String profile : new String[]{"local", "vpn", "test"}) {
      var yaml = new YamlPropertiesFactoryBean();
      yaml.setResources(new ClassPathResource("application.yml"),
        new ClassPathResource("application-" + profile + ".yml"));
      var properties = yaml.getObject();
      assertThat(properties.getProperty("spring.flyway.enabled")).as(profile).isEqualTo("true");
      assertThat(properties.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("false");
      assertThat(properties.getProperty("spring.flyway.validate-on-migrate")).isEqualTo("true");
    }
  }
}
