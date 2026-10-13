package com.sts.training.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sts.training.entity.TrainingPlanChange;
import org.apache.ibatis.annotations.Mapper;
/** 独立变更持久化。 */
@Mapper public interface TrainingPlanChangeMapper extends BaseMapper<TrainingPlanChange> {}
