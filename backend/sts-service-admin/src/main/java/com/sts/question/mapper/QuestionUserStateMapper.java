package com.sts.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sts.question.entity.QuestionUserState;
import org.apache.ibatis.annotations.*;

@Mapper
public interface QuestionUserStateMapper extends BaseMapper<QuestionUserState> {
    /** 初始化本人题目状态，存在时不改收藏、计数或错题处理水位。 */
    @Insert("""
      INSERT INTO sts_question_user_state (organization_id,user_id,question_id,is_favorite,wrong_count,correct_count,create_by,update_by)
      VALUES (#{organizationId},#{userId},#{questionId},0,0,0,#{userId},#{userId})
      ON DUPLICATE KEY UPDATE user_question_state_id=user_question_state_id
      """)
    int ensurePersonalState(@Param("organizationId") Long organizationId, @Param("userId") Long userId, @Param("questionId") Long questionId);
}
