package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.common.exception.BusinessException;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.port.TrainingCourseExecutionPort;
import com.sts.training.vo.TrainingExecutionLinkVO;
import com.sts.training.vo.TrainingGenerateExecutionFailureVO;
import com.sts.training.vo.TrainingGenerateExecutionsResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 计划级执行生成：逐内容调用独立事务的 provision，失败不回滚已成功内容。
 */
@Service
@RequiredArgsConstructor
public class TrainingExecutionServiceImpl implements TrainingExecutionService {

  private final TrainingPlanMapper planMapper;
  private final TrainingPlanStageMapper stageMapper;
  private final TrainingPlanContentMapper contentMapper;
  private final TrainingExecutionLinkMapper executionLinkMapper;
  private final TrainingOfflineSessionMapper offlineSessionMapper;
  private final TrainingExecutionProvisioner provisioner;
  private final TrainingCourseExecutionPort coursePort;

  @Override
  public TrainingGenerateExecutionsResultVO generateExecutions(Long trainingPlanId) {
    TrainingPlan plan = requirePlan(trainingPlanId);
    if (!"PUBLISHED".equals(plan.getPublishStatus()) && !"RUNNING".equals(plan.getPublishStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8108, "仅已发布计划可生成执行实例");
    }
    TrainingGenerateExecutionsResultVO result = new TrainingGenerateExecutionsResultVO();
    result.setTrainingPlanId(trainingPlanId);
    List<TrainingPlanStage> stages = stageMapper.selectList(new LambdaQueryWrapper<TrainingPlanStage>()
                                                              .eq(TrainingPlanStage::getTrainingPlanId, trainingPlanId)
                                                              .orderByAsc(TrainingPlanStage::getSortOrder));
    for (TrainingPlanStage stage : stages) {
      List<TrainingPlanContent> contents = contentMapper.selectList(
        new LambdaQueryWrapper<TrainingPlanContent>()
          .eq(TrainingPlanContent::getPlanStageId, stage.getPlanStageId())
          .orderByAsc(TrainingPlanContent::getSortOrder));
      for (TrainingPlanContent content : contents) {
        boolean existed = alreadyHasExecution(content);
        try {
          provisioner.provision(trainingPlanId, content.getPlanContentId());
          if (existed) {
            result.setSkippedExisting(result.getSkippedExisting() + 1);
          } else if ("OFFLINE".equals(content.getContentType())) {
            result.setOfflineSessionsCreated(result.getOfflineSessionsCreated() + 1);
          } else {
            result.setExecutionLinksCreated(result.getExecutionLinksCreated() + 1);
          }
        } catch (BusinessException ex) {
          result.getFailures().add(failure(content, ex.getCode(), ex.getMessage()));
        } catch (RuntimeException ex) {
          result.getFailures().add(failure(content, 8143, "执行生成失败"));
        }
      }
    }
    result.setFailedCount(result.getFailures().size());
    return result;
  }

  @Override
  public TrainingExecutionLinkVO provision(Long trainingPlanId, Long planContentId) {
    return provisioner.provision(trainingPlanId, planContentId);
  }

  private boolean alreadyHasExecution(TrainingPlanContent content) {
    if ("OFFLINE".equals(content.getContentType())) {
      return offlineSessionMapper.selectOne(new LambdaQueryWrapper<TrainingOfflineSession>()
                                              .eq(TrainingOfflineSession::getPlanContentId, content.getPlanContentId())
                                              .last("LIMIT 1")) != null;
    }
    return executionLinkMapper.selectOne(new LambdaQueryWrapper<TrainingExecutionLink>()
                                           .eq(TrainingExecutionLink::getPlanContentId, content.getPlanContentId())
                                           .eq(TrainingExecutionLink::getExecutionType, content.getContentType()))
      != null;
  }

  private TrainingGenerateExecutionFailureVO failure(TrainingPlanContent content, int code, String message) {
    TrainingGenerateExecutionFailureVO vo = new TrainingGenerateExecutionFailureVO();
    vo.setPlanContentId(content.getPlanContentId());
    vo.setContentType(content.getContentType());
    vo.setCode(code);
    vo.setMessage(message);
    if ("COURSE".equals(content.getContentType())) {
      vo.setOfferingId(coursePort.findOfferingIdByPlanContentId(content.getPlanContentId()));
    }
    return vo;
  }

  private TrainingPlan requirePlan(Long id) {
    TrainingPlan plan = planMapper.selectById(id);
    if (plan == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8101, "培训计划不存在");
    }
    return plan;
  }
}
