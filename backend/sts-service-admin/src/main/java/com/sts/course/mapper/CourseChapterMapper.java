package com.sts.course.mapper;

import com.sts.admin.mapper.AppBaseMapper;
import com.sts.course.entity.CourseChapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CourseChapterMapper extends AppBaseMapper<CourseChapter> {
  /** 课程锁取得后读取最新父关系，避免复用事务此前的RR快照。 */
  @Select("SELECT * FROM sts_course_chapter WHERE chapter_id = #{chapterId} AND deleted = 0 FOR UPDATE")
  CourseChapter selectCurrent(@Param("chapterId") Long chapterId);
}
