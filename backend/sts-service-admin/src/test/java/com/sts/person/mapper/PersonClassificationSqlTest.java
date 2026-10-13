package com.sts.person.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.sts.person.dto.PersonClassificationCondition;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

/** 无数据库环境先验证真实MyBatis条件展开与参数绑定；不替代MySQL IT。 */
class PersonClassificationSqlTest {
  @Test void fixedConditionsRenderWithoutInjectingKeywordIntoSql() throws Exception {
    MybatisConfiguration config = new MybatisConfiguration();
    String resource = "mapper/person/PersonClassificationMapper.xml";
    try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
      assertThat(stream).isNotNull();
      new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
    }
    PersonClassificationCondition c = new PersonClassificationCondition();
    c.setKeyword("' OR 1=1 --");c.setOccupation("电工");c.setGenders(List.of("MALE","FEMALE"));
    c.setEducations(List.of("本科"));c.setDeptIds(List.of(3L));c.setPositionIds(List.of(4L,5L));c.setTagIds(List.of(6L));
    c.setTrainingPlanId(7L);c.setMinAge(18);c.setMaxAge(60);
    var sql=config.getMappedStatement(PersonClassificationMapper.class.getName()+".resolve")
      .getBoundSql(Map.of("organizationId",1L,"condition",c,"today",LocalDate.of(2026,10,11)));
    assertThat(sql.getSql()).contains("FIND_IN_SET", "TIMESTAMPDIFF", "sts_org_user_position", "sts_org_user_tag", "sts_training_participant")
      .doesNotContain(c.getKeyword(),c.getOccupation());
    assertThat(sql.getParameterMappings()).isNotEmpty();
  }
}
