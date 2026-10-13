package com.sts.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sts.question.entity.LearnerPracticeSession;
import org.apache.ibatis.annotations.Mapper;

/** 个人练习记录的租户隔离数据访问。 */
@Mapper
public interface LearnerPracticeSessionMapper extends BaseMapper<LearnerPracticeSession> { }
