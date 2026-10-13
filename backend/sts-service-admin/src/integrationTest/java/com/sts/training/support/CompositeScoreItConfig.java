package com.sts.training.support;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.sts.admin.config.MybatisMetaObjectHandler;
import com.sts.admin.config.MybatisPlusConfig;
import com.sts.training.service.CompositeScoreService;
import com.sts.training.service.CompositeScoreServiceImpl;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 综合分自动计算 IT：真实评价规则、奖惩与参训人行。
 */
@SpringBootConfiguration
@ImportAutoConfiguration({
  DataSourceAutoConfiguration.class,
  DataSourceTransactionManagerAutoConfiguration.class,
  JdbcTemplateAutoConfiguration.class,
  MybatisPlusAutoConfiguration.class,
  TransactionAutoConfiguration.class
})
@EnableTransactionManagement
@MapperScan({
  "com.sts.training.mapper",
  "com.sts.evaluation.mapper",
  "com.sts.org.mapper",
  "com.sts.course.mapper"
})
@Import({MybatisPlusConfig.class, MybatisMetaObjectHandler.class})
public class CompositeScoreItConfig {

  @Bean
  CompositeScoreService compositeScoreService(
    com.sts.training.mapper.TrainingPlanEvaluationFormMapper bindingMapper,
    com.sts.evaluation.mapper.EvaluationFormVersionMapper formVersionMapper,
    com.sts.evaluation.mapper.EvaluationFormMapper formMapper,
    com.sts.evaluation.mapper.EvaluationScoreRuleMapper scoreRuleMapper,
    com.sts.training.mapper.TrainingRewardPenaltyMapper rewardPenaltyMapper) {
    return new CompositeScoreServiceImpl(
      bindingMapper, formVersionMapper, formMapper, scoreRuleMapper, rewardPenaltyMapper);
  }
}
