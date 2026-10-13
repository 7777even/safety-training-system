package com.sts.training.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sts.training.entity.TrainingPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TrainingPlanMapper extends BaseMapper<TrainingPlan> {
  /** 审批与修改共同锁定计划；显式租户条件和逻辑删除保护。 */
  @Select("SELECT * FROM sts_training_plan WHERE training_plan_id=#{id} AND organization_id=#{tenant} AND deleted=0 FOR UPDATE")
  TrainingPlan lockForApproval(@Param("id") Long id, @Param("tenant") Long tenant);
}
