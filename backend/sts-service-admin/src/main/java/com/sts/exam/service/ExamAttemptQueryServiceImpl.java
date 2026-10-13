package com.sts.exam.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.exam.dto.ExamAttemptRecordQuery;
import com.sts.exam.dto.ExamReviewTaskQuery;
import com.sts.exam.mapper.ExamAttemptQueryMapper;
import com.sts.exam.vo.ExamAttemptRecordVO;
import com.sts.exam.vo.ExamReviewTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class ExamAttemptQueryServiceImpl implements ExamAttemptQueryService {
  private final ExamAttemptQueryMapper queryMapper;

  @Override
  public PageResult<ExamReviewTaskVO> pageReviewTasks(PageRequest<ExamReviewTaskQuery> request) {
    Page<ExamReviewTaskVO> page = queryMapper.selectReviewTaskPage(
      new Page<>(request.getPageNum(), request.getPageSize()), StpUtil.getLoginIdAsLong(),
      null, false, request.getQuery());
    return new PageResult<>(page.getRecords(), page.getTotal());
  }

  @Override
  public PageResult<ExamAttemptRecordVO> pageAttempts(PageRequest<ExamAttemptRecordQuery> request) {
    // 可见范围由租户插件在 SQL 层注入，不再解析角色数据范围
    Page<ExamAttemptRecordVO> page = queryMapper.selectAttemptPage(
      new Page<>(request.getPageNum(), request.getPageSize()), request.getQuery());
    return new PageResult<>(page.getRecords(), page.getTotal());
  }
}
