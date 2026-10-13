package com.sts.evaluation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.evaluation.entity.EvaluationRecord;
import com.sts.evaluation.entity.EvaluationRecordItem;
import com.sts.evaluation.mapper.EvaluationRecordItemMapper;
import com.sts.evaluation.mapper.EvaluationRecordMapper;
import com.sts.evaluation.vo.EvaluationRecordItemVO;
import com.sts.evaluation.vo.EvaluationRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EvaluationRecordServiceImpl extends BaseServiceImpl<EvaluationRecordMapper, EvaluationRecord>
  implements EvaluationRecordService {

  private final EvaluationRecordMapper evaluationRecordMapper;
  private final EvaluationRecordItemMapper evaluationRecordItemMapper;

  @Override
  public EvaluationRecordVO get(Long evaluationRecordId) {
    EvaluationRecord record = requireRecord(evaluationRecordId);
    EvaluationRecordVO vo = new EvaluationRecordVO();
    BeanUtils.copyProperties(record, vo);
    List<EvaluationRecordItem> items = evaluationRecordItemMapper.selectList(
      new LambdaQueryWrapper<EvaluationRecordItem>().eq(EvaluationRecordItem::getEvaluationRecordId, evaluationRecordId));
    vo.setItems(items.stream().map(i -> {
      EvaluationRecordItemVO iv = new EvaluationRecordItemVO();
      BeanUtils.copyProperties(i, iv);
      return iv;
    }).toList());
    return vo;
  }

  private EvaluationRecord requireRecord(Long id) {
    EvaluationRecord record = evaluationRecordMapper.selectById(id);
    if (record == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8701, "评价记录不存在");
    }
    return record;
  }
}
