package com.sts.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sts.course.entity.CourseFavorite;
import org.apache.ibatis.annotations.*;

/** 课程收藏租户隔离查询与原子幂等写入。 */
@Mapper
public interface CourseFavoriteMapper extends BaseMapper<CourseFavorite> {
  /** 数据库唯一键保证重复收藏/取消及并发重加只保留一个关系。 */
  @Insert("""
    INSERT INTO sts_course_favorite (organization_id,user_id,course_id,is_favorite,course_name_snapshot,create_by,update_by)
    VALUES (#{organizationId},#{userId},#{courseId},#{isFavorite},#{courseNameSnapshot},#{userId},#{userId})
    ON DUPLICATE KEY UPDATE is_favorite=VALUES(is_favorite),course_name_snapshot=VALUES(course_name_snapshot),
      update_by=VALUES(update_by),update_time=CURRENT_TIMESTAMP,version=version+1
    """)
  int upsertFavorite(CourseFavorite row);
}
