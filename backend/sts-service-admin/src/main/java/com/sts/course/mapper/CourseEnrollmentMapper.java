package com.sts.course.mapper;

import com.sts.admin.mapper.AppBaseMapper;
import com.sts.course.entity.CourseEnrollment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseEnrollmentMapper extends AppBaseMapper<CourseEnrollment> {
}
