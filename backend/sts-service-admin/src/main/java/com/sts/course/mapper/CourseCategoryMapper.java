package com.sts.course.mapper;

import com.sts.admin.mapper.AppBaseMapper;
import com.sts.course.entity.CourseCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseCategoryMapper extends AppBaseMapper<CourseCategory> {
}
