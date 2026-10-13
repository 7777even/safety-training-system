package com.sts.survey.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sts.survey.entity.Survey;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SurveyMapper extends BaseMapper<Survey> {
}
