package com.sts.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.exam.dto.ExamAttemptRecordQuery;
import com.sts.exam.dto.ExamReviewTaskQuery;
import com.sts.exam.entity.ExamAttempt;
import com.sts.exam.vo.ExamAttemptRecordVO;
import com.sts.exam.vo.ExamReviewTaskVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


@Mapper
public interface ExamAttemptQueryMapper extends BaseMapper<ExamAttempt> {
  Page<ExamReviewTaskVO> selectReviewTaskPage(Page<ExamReviewTaskVO> page,
                                              @Param("reviewerUserId") Long reviewerUserId,
                                              @Param("assessmentExecutionId") Long assessmentExecutionId,
                                              @Param("courseOnly") boolean courseOnly,
                                              @Param("query") ExamReviewTaskQuery query);

  Page<ExamAttemptRecordVO> selectAttemptPage(Page<ExamAttemptRecordVO> page,
                                              @Param("query") ExamAttemptRecordQuery query);
}
