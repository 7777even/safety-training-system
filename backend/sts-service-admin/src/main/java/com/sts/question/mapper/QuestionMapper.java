package com.sts.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sts.question.entity.Question;
import com.sts.question.vo.QuestionDrawCandidateRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

  /**
   * 读取真实抽题候选。版本口径与开考一致：每题取版本号最大的已发布版本。
   */
  @Select("""
    SELECT q.question_id,
           v.question_version_id,
           v.question_type,
           v.difficulty,
           v.grading_mode,
           qk.knowledge_point_id,
           (SELECT COUNT(*)
              FROM sts_question_option qo
             WHERE qo.question_version_id = v.question_version_id
               AND qo.is_correct = 1) AS correct_answer_count,
           (SELECT COUNT(*)
              FROM sts_question_blank_answer ba
             WHERE ba.question_version_id = v.question_version_id) AS blank_answer_count
      FROM sts_question_question q
      INNER JOIN sts_question_version v
        ON v.question_id = q.question_id
       AND v.published_status = 'PUBLISHED'
       AND v.version_no = (
         SELECT MAX(v2.version_no)
           FROM sts_question_version v2
          WHERE v2.question_id = q.question_id
            AND v2.published_status = 'PUBLISHED'
       )
      LEFT JOIN sts_question_question_knowledge qk ON qk.question_id = q.question_id
     WHERE q.question_bank_id = #{questionBankId}
       AND q.deleted = 0
       AND q.status = 1
       AND q.archive_status = 'ACTIVE'
     ORDER BY q.question_id, qk.knowledge_point_id
    """)
  List<QuestionDrawCandidateRow> selectDrawCandidateRows(@Param("questionBankId") Long questionBankId);
}
