package com.sts.exam.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.exam.dto.ExamCandidateQuery;
import com.sts.exam.vo.ExamCandidateVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 考试人员分页读取。展示字段优先取冻结快照，快照缺失才回退实时用户数据。
 */
@Mapper
public interface ExamCandidateQueryMapper {

  IPage<ExamCandidateVO> selectCandidatePage(Page<ExamCandidateVO> page,
                                             @Param("examId") Long examId,
                                             @Param("query") ExamCandidateQuery query);
}
