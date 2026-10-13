package com.sts.admin.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 本批业务Mapper独立切片，不扩大原管理Mapper测试的扫描范围。 */
@Configuration
@Import(MapperItConfig.class)
@MapperScan({"com.sts.question.mapper", "com.sts.course.mapper", "com.sts.exam.mapper"})
public class LearnerBatchMapperItConfig { }
