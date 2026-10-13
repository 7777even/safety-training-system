package com.sts.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.exception.BusinessException;
import com.sts.exam.entity.AssessmentExecutionQuestionPool;
import com.sts.exam.mapper.AssessmentExecutionQuestionPoolMapper;
import com.sts.question.draw.QuestionDrawEngine;
import com.sts.question.draw.QuestionPoolFingerprint;
import com.sts.training.port.TrainingQuestionPoolItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 复制、复用并校验考核执行自己的冻结题池。
 */
@Service
@RequiredArgsConstructor
public class AssessmentExecutionQuestionPoolService {
  private final AssessmentExecutionQuestionPoolMapper poolMapper;
  private final ObjectMapper objectMapper;

  public void ensure(Long executionId, Long sourcePlanContentId, Long questionBankId,
                     String poolHash, List<TrainingQuestionPoolItem> items) {
    List<AssessmentExecutionQuestionPool> existing = rows(executionId);
    if (!existing.isEmpty()) {
      assertMatches(existing, sourcePlanContentId, poolHash, items.size());
      return;
    }
    for (TrainingQuestionPoolItem item : items) {
      AssessmentExecutionQuestionPool row = new AssessmentExecutionQuestionPool();
      row.setAssessmentExecutionId(executionId);
      row.setSourcePlanContentId(sourcePlanContentId);
      row.setQuestionBankId(questionBankId);
      row.setQuestionId(item.getQuestionId());
      row.setQuestionVersionId(item.getQuestionVersionId());
      row.setQuestionType(item.getQuestionType());
      row.setDifficulty(item.getDifficulty());
      row.setKnowledgePointIdsJson(write(item.getKnowledgePointIds()));
      row.setEvaluationMode(item.getEvaluationMode());
      row.setCandidateOrder(item.getCandidateOrder());
      row.setPoolHash(poolHash);
      poolMapper.insert(row);
    }
  }

  public List<QuestionDrawEngine.Candidate> loadCandidates(Long executionId, String expectedHash) {
    List<AssessmentExecutionQuestionPool> rows = rows(executionId);
    List<TrainingQuestionPoolItem> items = rows.stream().map(this::toItem).toList();
    if (items.isEmpty() || !Objects.equals(expectedHash, fingerprint(items))
      || rows.stream().anyMatch(row -> !Objects.equals(expectedHash, row.getPoolHash()))) {
      throw invalid("考核执行冻结题池缺失或指纹不一致");
    }
    try {
      return items.stream().map(item -> new QuestionDrawEngine.Candidate(
        item.getQuestionId(), item.getQuestionVersionId(), item.getQuestionType(), item.getDifficulty(),
        item.getKnowledgePointIds() == null ? java.util.Set.of() : java.util.Set.copyOf(item.getKnowledgePointIds()),
        QuestionDrawEngine.EvaluationMode.valueOf(item.getEvaluationMode()))).toList();
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw invalid("考核执行冻结题池判定模式无效");
    }
  }

  private void assertMatches(List<AssessmentExecutionQuestionPool> rows, Long sourceContentId,
                             String hash, int expectedCount) {
    List<TrainingQuestionPoolItem> items = rows.stream().map(this::toItem).toList();
    boolean valid = rows.size() == expectedCount
      && rows.stream().allMatch(row -> Objects.equals(sourceContentId, row.getSourcePlanContentId())
      && Objects.equals(hash, row.getPoolHash()))
      && Objects.equals(hash, fingerprint(items));
    if (!valid) {
      throw invalid("既有执行题池与计划发布题池不一致");
    }
  }

  private String fingerprint(List<TrainingQuestionPoolItem> items) {
    return QuestionPoolFingerprint.of(items.stream().map(item -> new QuestionPoolFingerprint.Item(
      item.getQuestionId(), item.getQuestionVersionId(), item.getQuestionType(), item.getDifficulty(),
      item.getKnowledgePointIds(), item.getEvaluationMode())).toList());
  }

  private List<AssessmentExecutionQuestionPool> rows(Long executionId) {
    return poolMapper.selectList(new LambdaQueryWrapper<AssessmentExecutionQuestionPool>()
                                   .eq(AssessmentExecutionQuestionPool::getAssessmentExecutionId, executionId)
                                   .orderByAsc(AssessmentExecutionQuestionPool::getCandidateOrder));
  }

  private TrainingQuestionPoolItem toItem(AssessmentExecutionQuestionPool row) {
    TrainingQuestionPoolItem item = new TrainingQuestionPoolItem();
    item.setQuestionId(row.getQuestionId());
    item.setQuestionVersionId(row.getQuestionVersionId());
    item.setQuestionType(row.getQuestionType());
    item.setDifficulty(row.getDifficulty());
    try {
      item.setKnowledgePointIds(objectMapper.readValue(row.getKnowledgePointIdsJson(), new TypeReference<>() {
      }));
    } catch (Exception ex) {
      throw invalid("执行题池知识点快照无法解析");
    }
    item.setEvaluationMode(row.getEvaluationMode());
    item.setCandidateOrder(row.getCandidateOrder());
    return item;
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw invalid("执行题池知识点快照无法序列化");
    }
  }

  private BusinessException invalid(String message) {
    return new BusinessException(HttpStatus.CONFLICT,
                                 8167, message);
  }
}
