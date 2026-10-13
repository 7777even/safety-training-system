package com.sts.exam.mapper;

import com.sts.exam.vo.ExamReviewerVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 批阅人读取。需要跨 sys_user 与机构表取展示信息，故独立于 MyBatis-Plus 的单表 Mapper。
 */
@Mapper
public interface ExamReviewerQueryMapper {

  List<ExamReviewerVO> selectReviewers(@Param("assessmentExecutionId") Long assessmentExecutionId);
}
