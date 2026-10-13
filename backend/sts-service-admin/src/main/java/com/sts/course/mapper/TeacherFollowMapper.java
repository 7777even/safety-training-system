package com.sts.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sts.course.entity.TeacherFollow;
import com.sts.course.vo.TeacherPublicVO;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 教师目录沿用角色关联，租户插件同时隔离用户与角色两端。 */
@Mapper
public interface TeacherFollowMapper extends BaseMapper<TeacherFollow> {
  /** DISTINCT避免多个教师角色产生重复教师；明确不选择敏感字段。 */
  @Select("""
    SELECT DISTINCT u.user_id AS teacher_id,u.user_name AS teacher_name
    FROM us_sys_user u JOIN us_sys_user_role ur ON ur.user_id=u.user_id
    JOIN us_sys_role r ON r.role_id=ur.role_id AND r.organization_id=u.organization_id
    WHERE u.deleted=0 AND u.status=1 AND r.deleted=0 AND r.status=1 AND r.role_level='TEACHER'
      AND (#{keyword} IS NULL OR u.user_name LIKE CONCAT('%',#{keyword},'%'))
      AND (#{teacherId} IS NULL OR u.user_id=#{teacherId})
    ORDER BY u.user_id DESC
    """)
  List<TeacherPublicVO> selectTeachers(@Param("keyword") String keyword, @Param("teacherId") Long teacherId);
  /** 唯一键保证关注、取消、重新关注与并发重复请求只保留一份关系。 */
  @Insert("""
    INSERT INTO sts_teacher_follow (organization_id,user_id,teacher_id,is_followed,teacher_name_snapshot,create_by,update_by)
    VALUES (#{organizationId},#{userId},#{teacherId},#{isFollowed},#{teacherNameSnapshot},#{userId},#{userId})
    ON DUPLICATE KEY UPDATE is_followed=VALUES(is_followed),teacher_name_snapshot=VALUES(teacher_name_snapshot),
      update_by=VALUES(update_by),update_time=CURRENT_TIMESTAMP,version=version+1
    """)
  int upsertFollow(TeacherFollow row);
}
