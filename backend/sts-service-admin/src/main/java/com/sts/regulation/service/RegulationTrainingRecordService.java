package com.sts.regulation.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.TrainingPlanQuery;
import com.sts.training.vo.TrainingParticipantResultsVO;
import com.sts.training.vo.TrainingParticipantVO;
import com.sts.training.vo.TrainingPlanVO;

import java.util.List;

/**
 * 监管只读下钻目标企业的培训计划、参训人与内容结果。
 */
public interface RegulationTrainingRecordService {

  PageResult<TrainingPlanVO> pagePlans(PageRequest<TrainingPlanQuery> request);

  TrainingPlanVO getPlan(Long trainingPlanId);

  List<TrainingParticipantVO> listParticipants(Long trainingPlanId);

  TrainingParticipantResultsVO getResults(Long trainingPlanId, Long trainingParticipantId);
}
