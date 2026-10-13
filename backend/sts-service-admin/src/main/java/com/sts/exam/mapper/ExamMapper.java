package com.sts.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sts.exam.entity.Exam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExamMapper extends BaseMapper<Exam> {
  @Select("SELECT * FROM sts_exam_exam WHERE exam_id = #{examId} AND deleted = 0 FOR UPDATE")
  Exam selectByIdForUpdate(@Param("examId") Long examId);
}
