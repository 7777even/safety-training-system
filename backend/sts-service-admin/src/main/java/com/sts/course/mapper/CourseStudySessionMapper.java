package com.sts.course.mapper;

import com.sts.admin.mapper.AppBaseMapper;
import com.sts.course.entity.CourseStudySession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface CourseStudySessionMapper extends AppBaseMapper<CourseStudySession> {

  /**
   * 查询同一用户在其他学习上下文中的活跃会话（互斥检测）。
   * <p>
   * 上下文 = 报名 + 内容项 + 终端；活跃 = 该上下文最新一行在窗口内且为 HEARTBEAT
   * （USER_EXIT 等显式收尾立即释放，仅依赖心跳窗口兜底）。排除当前上下文，
   * 覆盖同一账户多终端、同一账户同时学习多个培训内容两种互斥。
   */
  @Select("""
    SELECT * FROM sts_course_study_session s
    WHERE s.user_id = #{userId} AND s.deleted = 0 AND s.update_time >= #{since}
      AND s.end_reason = 'HEARTBEAT'
      AND NOT (s.course_enrollment_id = #{enrollmentId} AND s.course_item_id = #{itemId} AND s.device_id = #{deviceId})
      AND s.update_time = (SELECT MAX(x.update_time) FROM sts_course_study_session x
        WHERE x.user_id = s.user_id AND x.deleted = 0
          AND x.course_enrollment_id = s.course_enrollment_id AND x.course_item_id = s.course_item_id
          AND x.device_id = s.device_id)
    LIMIT 1
    """)
  CourseStudySession selectOtherActiveContext(@Param("userId") Long userId, @Param("enrollmentId") Long enrollmentId,
      @Param("itemId") Long itemId, @Param("deviceId") String deviceId, @Param("since") LocalDateTime since);

  /**
   * 查询同一终端上其他账户的活跃会话（互斥检测），覆盖同一终端多账户同时学习。
   * 终端标识为客户端持久化的随机值，按用户与终端身份判定，不依赖租户上下文。
   */
  @Select("""
    SELECT * FROM sts_course_study_session s
    WHERE s.device_id = #{deviceId} AND s.user_id <> #{userId} AND s.deleted = 0
      AND s.update_time >= #{since} AND s.end_reason = 'HEARTBEAT'
      AND s.update_time = (SELECT MAX(x.update_time) FROM sts_course_study_session x
        WHERE x.device_id = s.device_id AND x.user_id = s.user_id AND x.deleted = 0
          AND x.course_enrollment_id = s.course_enrollment_id AND x.course_item_id = s.course_item_id)
    LIMIT 1
    """)
  CourseStudySession selectOtherUserActiveOnDevice(@Param("deviceId") String deviceId, @Param("userId") Long userId,
      @Param("since") LocalDateTime since);
}
