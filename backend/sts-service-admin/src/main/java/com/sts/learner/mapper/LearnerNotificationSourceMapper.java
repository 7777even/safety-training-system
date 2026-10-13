package com.sts.learner.mapper;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** 只取消息需要的人员与任务摘要，所有业务连接保持同机构、未删除约束。 */
@Mapper
public interface LearnerNotificationSourceMapper {
  /** 消息来源与受控业务引用，状态仅用于提醒判断。 */
  record Target(Long userId, String kind, Long resourceId, Long parentId, Long itemId,
                String title, String status, LocalDateTime startTime, LocalDateTime endTime) { }

  /** 当前独立课程分配；培训课程由培训入口通知，避免重复和提前可见。 */
  @Select("""
    <script>
    SELECT e.user_id,'COURSE' kind,e.course_enrollment_id resource_id,NULL parent_id,NULL item_id,
      o.offering_name title,e.learning_status status,o.start_time,o.end_time
    FROM sts_course_enrollment e JOIN sts_course_offering o
      ON o.course_offering_id=e.course_offering_id AND o.organization_id=e.organization_id
    WHERE e.deleted=0 AND o.deleted=0 AND e.organization_id=#{org} AND e.enrollment_source='ASSIGN'
      AND o.status=1 AND o.publish_status='PUBLISHED'
      <if test="id != null">AND o.course_offering_id=#{id}</if>
    </script>
    """)
  List<Target> courses(@Param("org") Long org, @Param("id") Long offeringId);

  /** 已发布独立考试，保留实际考生引用；培训考试从培训任务进入。 */
  @Select("""
    <script>
    SELECT c.user_id,'FORMAL_EXAM' kind,c.exam_candidate_id resource_id,e.exam_id parent_id,NULL item_id,
      e.exam_name title,c.candidate_status status,e.open_start_time start_time,e.open_end_time end_time
    FROM sts_exam_candidate c JOIN sts_assessment_execution x
      ON x.assessment_execution_id=c.assessment_execution_id AND x.organization_id=c.organization_id
    JOIN sts_exam_exam e ON e.exam_id=x.exam_id AND e.organization_id=c.organization_id
    WHERE c.deleted=0 AND x.deleted=0 AND e.deleted=0 AND c.organization_id=#{org}
      AND e.status=1 AND e.publish_status IN ('PUBLISHED','RUNNING')
      AND x.execution_status='READY'
      AND x.source_type NOT IN ('TRAINING_EXAM','TRAINING_PRACTICE')
      <if test="id != null">AND e.exam_id=#{id}</if>
    </script>
    """)
  List<Target> exams(@Param("org") Long org, @Param("id") Long examId);

  /** 培训发布/启动/取消通知均取已冻结的参训名单。 */
  @Select("""
    <script>
    SELECT p.user_id,'TRAINING' kind,p.training_participant_id resource_id,t.training_plan_id parent_id,NULL item_id,
      t.plan_name title,CASE WHEN t.publish_status IN ('PUBLISHED','RUNNING') THEN p.participant_status ELSE t.publish_status END status,
      t.start_time,t.end_time
    FROM sts_training_participant p JOIN sts_training_plan t
      ON t.training_plan_id=p.training_plan_id AND t.organization_id=p.organization_id
    WHERE p.deleted=0 AND t.deleted=0 AND p.organization_id=#{org} AND t.status=1
      <choose><when test="id != null">AND t.training_plan_id=#{id}</when>
        <otherwise>AND t.publish_status='RUNNING'</otherwise></choose>
    </script>
    """)
  List<Target> trainings(@Param("org") Long org, @Param("id") Long planId);

  /** 已报名课程的测验通知，不要求学员先启动作答才有候选人。 */
  @Select("""
    <script>
    SELECT e.user_id,'COURSE_QUIZ' kind,e.course_enrollment_id resource_id,NULL parent_id,x.course_item_id item_id,
      CONCAT(o.offering_name,' · 随堂测试') title,e.learning_status status,o.start_time,o.end_time
    FROM sts_course_enrollment e JOIN sts_course_offering o
      ON o.course_offering_id=e.course_offering_id AND o.organization_id=e.organization_id
    JOIN sts_assessment_execution x ON x.course_offering_id=o.course_offering_id AND x.organization_id=e.organization_id
    WHERE e.deleted=0 AND o.deleted=0 AND x.deleted=0 AND e.organization_id=#{org}
      AND e.enrollment_source='ASSIGN' AND o.status=1 AND o.publish_status='PUBLISHED' AND x.assessment_kind='QUIZ'
      AND x.execution_status='READY'
      <if test="id != null">AND o.course_offering_id=#{id}</if>
    </script>
    """)
  List<Target> quizzes(@Param("org") Long org, @Param("id") Long offeringId);

  /** 课程上下架只通知已有报名/关注的人，UNION按学员去重。 */
  @Select("""
    SELECT e.user_id FROM sts_course_enrollment e JOIN sts_course_offering o
      ON o.course_offering_id=e.course_offering_id AND o.organization_id=e.organization_id
    WHERE e.organization_id=#{org} AND e.deleted=0 AND o.deleted=0 AND o.course_id=#{id}
    UNION
    SELECT f.user_id FROM sts_course_favorite f
    WHERE f.organization_id=#{org} AND f.course_id=#{id} AND f.deleted=0 AND f.is_favorite=1
    """)
  List<Long> courseAudience(@Param("org") Long org, @Param("id") Long courseId);

  /** 全局租户主数据属于既有豁免表，仅用于逐租户执行已登记提醒任务。 */
  @Select("SELECT organization_id FROM sts_org_organization WHERE deleted=0 AND status=1")
  List<Long> tenants();

  /** 发布状态与乐观锁版本区分真实重复上下架，幂等调用保持同一状态键。 */
  @Select("SELECT CONCAT(publish_status,':',COALESCE(current_version_id,0),':',version) FROM sts_course_course WHERE organization_id=#{org} AND course_id=#{id} AND deleted=0")
  String courseState(@Param("org") Long org, @Param("id") Long courseId);
}
