package com.sts.course.mapper;

import com.sts.admin.mapper.AppBaseMapper;
import com.sts.course.entity.CourseItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CourseItemMapper extends AppBaseMapper<CourseItem> {
  /** 课程锁后读取最新内容项，防止删除与课件关联并发造成失效引用。 */
  @Select("SELECT * FROM sts_course_item WHERE course_item_id = #{itemId} AND deleted = 0 FOR UPDATE")
  CourseItem selectCurrent(@Param("itemId") Long itemId);
}
