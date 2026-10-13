package com.sts.learner.mapper;

import com.sts.learner.vo.HistorySource;
import com.sts.archive.entity.ArchiveHistoryCorrection;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 历史查询故意不套当前任务、发布或逻辑删除筛选，退出后已有记录仍可追溯。 */
@Mapper
public interface LearnerHistoryMapper {
  /** 始终限制同机构本人；逻辑退出不抹去历史。 */
  @Select("SELECT user_id FROM us_sys_user WHERE organization_id=#{org} AND user_id=#{user}")
  Long userExists(@Param("org") Long org, @Param("user") Long user);
  /** 更正锁定学员行，串行追加版本；不修改该行。 */
  @Select("SELECT user_id FROM us_sys_user WHERE organization_id=#{org} AND user_id=#{user} FOR UPDATE")
  Long lockUser(@Param("org") Long org, @Param("user") Long user);
  /** 操作人姓名固化到留痕。 */
  @Select("SELECT user_name FROM us_sys_user WHERE user_id=#{user}")
  String operatorName(@Param("user") Long user);

  /** 课程报名全部历史，学时来自既有累计秒数，不创建学习事件。 */
  @Select("""
    SELECT 'COURSE' record_type,e.course_enrollment_id record_id,COALESCE(o.offering_name,'历史课程') title,
      COALESCE(e.completed_time,e.last_study_time,e.enrolled_time,e.create_time) occurred_time,e.learning_status status,
      u.user_name,u.user_code,e.final_score score,COALESCE(e.recognized_study_seconds,e.total_study_seconds)/3600.0 hours,1 score_visible,
      99999999.99 max_score,'COURSE' reference_kind,e.course_enrollment_id resource_id,NULL parent_id,NULL item_id,
      NULL attempt_id,'VIEW_COURSE' action
    FROM sts_course_enrollment e LEFT JOIN sts_course_offering o
      ON o.course_offering_id=e.course_offering_id AND o.organization_id=e.organization_id
    LEFT JOIN us_sys_user u ON u.user_id=e.user_id AND u.organization_id=e.organization_id
    WHERE e.organization_id=#{org} AND e.user_id=#{user}
    """)
  List<HistorySource> courses(@Param("org") Long org, @Param("user") Long user);

  /** 考试与课程考核保留冻结身份、最新原答卷引用和原领域发布/生效规则。 */
  @Select("""
    SELECT CASE WHEN x.source_type IN ('COURSE_QUIZ','COURSE_BANK_PRACTICE') THEN 'COURSE_QUIZ' ELSE 'FORMAL_EXAM' END record_type,
      c.exam_candidate_id record_id,COALESCE(e.exam_name,JSON_UNQUOTE(JSON_EXTRACT(x.snapshot_json,'$.resource.name')),'历史考核') title,
      COALESCE(a.submitted_time,a.started_time,c.profile_snapshot_time,c.create_time) occurred_time,c.candidate_status status,
      JSON_UNQUOTE(JSON_EXTRACT(c.candidate_profile_snapshot_json,'$.userName')) user_name,
      JSON_UNQUOTE(JSON_EXTRACT(c.candidate_profile_snapshot_json,'$.userCode')) user_code,
      c.final_score score,NULL hours,
      CASE WHEN x.source_type='TRAINING_PRACTICE' AND a.total_score_snapshot IS NULL THEN 0
        WHEN x.source_type IN ('COURSE_QUIZ','COURSE_BANK_PRACTICE') OR (x.source_type='STANDALONE_PRACTICE' AND x.assessment_kind='BANK_PRACTICE')
          THEN a.attempt_status='RESULT_EFFECTIVE' AND a.result_effective_time IS NOT NULL
        ELSE a.attempt_status='PUBLISHED' AND a.score_published_time IS NOT NULL
          AND COALESCE(policy.is_score_visible,1) != 0 END score_visible,
      COALESCE(a.total_score_snapshot,CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(x.snapshot_json,'$.rules.totalScore')),'null') AS DECIMAL(10,2))) max_score,
      CASE WHEN c.training_participant_id > 0 THEN 'TRAINING'
        WHEN x.source_type IN ('COURSE_QUIZ','COURSE_BANK_PRACTICE') THEN 'COURSE_QUIZ' ELSE 'FORMAL_EXAM' END reference_kind,
      CASE WHEN c.training_participant_id > 0 THEN c.training_participant_id
        WHEN x.source_type IN ('COURSE_QUIZ','COURSE_BANK_PRACTICE') THEN c.course_enrollment_id
        ELSE c.exam_candidate_id END resource_id,
      x.exam_id parent_id,c.course_item_id item_id,a.exam_attempt_id attempt_id,
      CASE WHEN a.attempt_status IN ('CREATED','ANSWERING') THEN 'CONTINUE'
        WHEN a.exam_attempt_id IS NOT NULL THEN 'VIEW_RESULT' ELSE 'START' END action
    FROM sts_exam_candidate c LEFT JOIN sts_assessment_execution x
      ON x.assessment_execution_id=c.assessment_execution_id AND x.organization_id=c.organization_id
    LEFT JOIN sts_exam_exam e ON e.exam_id=x.exam_id AND e.organization_id=c.organization_id
    LEFT JOIN sts_exam_policy policy ON policy.exam_id=x.exam_id AND policy.organization_id=c.organization_id AND policy.deleted=0
    LEFT JOIN sts_exam_attempt a ON a.exam_attempt_id=COALESCE(c.final_attempt_id,
      (SELECT MAX(a2.exam_attempt_id) FROM sts_exam_attempt a2 WHERE a2.exam_candidate_id=c.exam_candidate_id
        AND a2.organization_id=c.organization_id AND a2.user_id=c.user_id)) AND a.organization_id=c.organization_id AND a.user_id=c.user_id
    WHERE c.organization_id=#{org} AND c.user_id=#{user}
    """)
  List<HistorySource> exams(@Param("org") Long org, @Param("user") Long user);

  /** 练习/模拟使用独立持久记录，不投影成正式考试成绩。 */
  @Select("""
    SELECT p.mode record_type,p.session_id record_id,COALESCE(p.title,'历史练习') title,COALESCE(p.submitted_time,p.started_time,p.create_time) occurred_time,
      p.status,u.user_name,u.user_code,NULL score,NULL hours,0 score_visible,NULL max_score,
      p.mode reference_kind,p.session_id resource_id,NULL parent_id,NULL item_id,NULL attempt_id,'VIEW_RESULT' action
    FROM sts_learner_practice_session p LEFT JOIN us_sys_user u ON u.user_id=p.user_id AND u.organization_id=p.organization_id
    WHERE p.organization_id=#{org} AND p.user_id=#{user}
    """)
  List<HistorySource> practices(@Param("org") Long org, @Param("user") Long user);

  /** 培训含退出与结束记录；只读取正式投影，不修改冻结结果。 */
  @Select("""
    SELECT 'TRAINING' record_type,p.training_participant_id record_id,COALESCE(t.plan_name,'历史培训') title,
      COALESCE(p.result_finalized_time,p.completed_time,p.joined_time,p.create_time) occurred_time,
      p.participant_status status,u.user_name,u.user_code,p.composite_score score,
      COALESCE(p.online_hours,0)+COALESCE(p.offline_hours,0) hours,
      CASE WHEN t.publish_status='ENDED' AND p.result_finalized_time IS NOT NULL THEN 1 ELSE 0 END score_visible,
      99999999.99 max_score,'TRAINING' reference_kind,p.training_participant_id resource_id,t.training_plan_id parent_id,
      NULL item_id,NULL attempt_id,'VIEW_TRAINING' action
    FROM sts_training_participant p LEFT JOIN sts_training_plan t
      ON t.training_plan_id=p.training_plan_id AND t.organization_id=p.organization_id
    LEFT JOIN us_sys_user u ON u.user_id=p.user_id AND u.organization_id=p.organization_id
    WHERE p.organization_id=#{org} AND p.user_id=#{user}
    """)
  List<HistorySource> trainings(@Param("org") Long org, @Param("user") Long user);

  /** 一次获取全部追加更正，避免历史列表逐行扫描留痕。 */
  @Select("SELECT * FROM sts_archive_history_correction WHERE organization_id=#{org} AND user_id=#{user} ORDER BY version,correction_id")
  List<ArchiveHistoryCorrection> corrections(@Param("org") Long org, @Param("user") Long user);
  /** 唯一写操作为追加；无更新或删除方法。 */
  @Insert("""
    INSERT INTO sts_archive_history_correction(organization_id,user_id,record_type,record_id,version,
      before_json,after_json,reason,operator_id,operator_name,operated_time)
    VALUES(#{organizationId},#{userId},#{recordType},#{recordId},#{version},#{beforeJson},#{afterJson},
      #{reason},#{operatorId},#{operatorName},#{operatedTime})
    """)
  @Options(useGeneratedKeys = true, keyProperty = "correctionId")
  void append(ArchiveHistoryCorrection correction);
}
