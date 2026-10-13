package com.sts.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.exam.entity.ExamAnticheatEvent;
import com.sts.exam.entity.ExamScorePublishLog;
import com.sts.exam.mapper.ExamAnticheatEventMapper;
import com.sts.exam.mapper.ExamScorePublishLogMapper;
import com.sts.exam.vo.ExamAnticheatEventVO;
import com.sts.exam.vo.ExamScorePublishLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamAttemptAuditQueryServiceImpl implements ExamAttemptAuditQueryService {

  private final ExamAnticheatEventMapper anticheatEventMapper;
  private final ExamScorePublishLogMapper scorePublishLogMapper;

  @Override
  public List<ExamAnticheatEventVO> listAnticheatEvents(Long examAttemptId) {
    return anticheatEventMapper.selectList(new LambdaQueryWrapper<ExamAnticheatEvent>()
                                             .eq(ExamAnticheatEvent::getExamAttemptId, examAttemptId)
                                             .orderByAsc(ExamAnticheatEvent::getEventTime))
      .stream().map(event -> {
        ExamAnticheatEventVO vo = new ExamAnticheatEventVO();
        BeanUtils.copyProperties(event, vo);
        return vo;
      }).toList();
  }

  @Override
  public List<ExamScorePublishLogVO> listScorePublishLogs(Long examAttemptId) {
    return scorePublishLogMapper.selectList(new LambdaQueryWrapper<ExamScorePublishLog>()
                                              .eq(ExamScorePublishLog::getExamAttemptId, examAttemptId)
                                              .orderByAsc(ExamScorePublishLog::getPublishedTime))
      .stream().map(log -> {
        ExamScorePublishLogVO vo = new ExamScorePublishLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
      }).toList();
  }
}
