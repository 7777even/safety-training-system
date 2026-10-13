package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.exception.BusinessException;
import com.sts.question.draw.QuestionDrawEngine;
import com.sts.question.draw.QuestionPoolFingerprint;
import com.sts.question.entity.QuestionBank;
import com.sts.training.dto.TrainingContentExecutionConfig;
import com.sts.training.dto.TrainingQuestionBankResourceSnapshot;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanContent;
import com.sts.training.entity.TrainingPlanQuestionPool;
import com.sts.training.mapper.TrainingPlanQuestionPoolMapper;
import com.sts.training.port.TrainingQuestionPoolItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 负责计划题池的规范化、持久化、指纹计算和完整性读取。
 */
@Service
@RequiredArgsConstructor
public class TrainingQuestionPoolSnapshotService {

  public static final int CODE_POOL_INVALID = 8167;

  private final TrainingPlanQuestionPoolMapper poolMapper;
  private final ObjectMapper objectMapper;

  public FrozenPool freeze(TrainingPlan plan, TrainingPlanContent content, QuestionBank bank,
                           TrainingContentExecutionConfig config,
                           List<QuestionDrawEngine.Candidate> candidates) {
    List<TrainingQuestionPoolItem> items = candidates.stream()
      .sorted(Comparator.comparingLong(QuestionDrawEngine.Candidate::questionId))
      .map(this::toItem).toList();
    for (int index = 0; index < items.size(); index++) {
      items.get(index).setCandidateOrder(index + 1);
    }
    String poolHash = fingerprint(items);
    poolMapper.delete(new LambdaQueryWrapper<TrainingPlanQuestionPool>()
                        .eq(TrainingPlanQuestionPool::getPlanContentId, content.getPlanContentId()));
    for (TrainingQuestionPoolItem item : items) {
      TrainingPlanQuestionPool row = toRow(plan, content, bank, item, poolHash);
      poolMapper.insert(row);
    }
    TrainingQuestionBankResourceSnapshot snapshot = new TrainingQuestionBankResourceSnapshot();
    snapshot.setSchemaVersion(2);
    snapshot.setContentType("QUESTION_BANK");
    snapshot.setResourceId(bank.getQuestionBankId());
    snapshot.setBankCode(bank.getBankCode());
    snapshot.setBankName(bank.getBankName());
    snapshot.setExecutionConfig(config);
    snapshot.setPoolHash(poolHash);
    snapshot.setCandidateCount(items.size());
    snapshot.setCandidates(items);
    try {
      String json = objectMapper.writeValueAsString(snapshot);
      content.setResourceSnapshotJson(json);
      content.setResourceSnapshotHash(canonicalHash(json));
    } catch (Exception ex) {
      throw invalid(content, "题库练习发布快照无法序列化");
    }
    return new FrozenPool(poolHash, items);
  }

  public FrozenPool load(TrainingPlanContent content) {
    TrainingQuestionBankResourceSnapshot snapshot = readSnapshot(content);
    List<TrainingPlanQuestionPool> rows = poolMapper.selectList(
      new LambdaQueryWrapper<TrainingPlanQuestionPool>()
        .eq(TrainingPlanQuestionPool::getPlanContentId, content.getPlanContentId())
        .orderByAsc(TrainingPlanQuestionPool::getCandidateOrder));
    List<TrainingQuestionPoolItem> items = rows.stream().map(this::toItem).toList();
    String actualHash = fingerprint(items);
    boolean rowHashesMatch = rows.stream().allMatch(row -> Objects.equals(snapshot.getPoolHash(), row.getPoolHash()));
    if (items.isEmpty() || !Objects.equals(snapshot.getCandidateCount(), items.size())
      || !Objects.equals(snapshot.getPoolHash(), actualHash) || !rowHashesMatch) {
      throw invalid(content, "题库练习冻结题池缺失或指纹不一致");
    }
    return new FrozenPool(actualHash, items);
  }

  private String fingerprint(List<TrainingQuestionPoolItem> items) {
    return QuestionPoolFingerprint.of(items.stream().map(item -> new QuestionPoolFingerprint.Item(
      item.getQuestionId(), item.getQuestionVersionId(), item.getQuestionType(), item.getDifficulty(),
      item.getKnowledgePointIds(), item.getEvaluationMode())).toList());
  }

  private TrainingQuestionBankResourceSnapshot readSnapshot(TrainingPlanContent content) {
    try {
      String json = content.getResourceSnapshotJson();
      if (json == null || !Objects.equals(content.getResourceSnapshotHash(), canonicalHash(json))) {
        throw invalid(content, "题库练习发布快照缺失或校验失败");
      }
      TrainingQuestionBankResourceSnapshot snapshot = objectMapper.readValue(
        json, TrainingQuestionBankResourceSnapshot.class);
      if (snapshot.getSchemaVersion() == null || snapshot.getSchemaVersion() != 2
        || !"QUESTION_BANK".equals(snapshot.getContentType()) || snapshot.getPoolHash() == null) {
        throw invalid(content, "题库练习发布快照版本无效");
      }
      return snapshot;
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      throw invalid(content, "题库练习发布快照无法解析");
    }
  }

  private TrainingQuestionPoolItem toItem(QuestionDrawEngine.Candidate candidate) {
    TrainingQuestionPoolItem item = new TrainingQuestionPoolItem();
    item.setQuestionId(candidate.questionId());
    item.setQuestionVersionId(candidate.questionVersionId());
    item.setQuestionType(candidate.questionType());
    item.setDifficulty(candidate.difficulty());
    item.setKnowledgePointIds(candidate.knowledgePointIds().stream().sorted().toList());
    item.setEvaluationMode(candidate.evaluationMode().name());
    return item;
  }

  private TrainingPlanQuestionPool toRow(TrainingPlan plan, TrainingPlanContent content, QuestionBank bank,
                                         TrainingQuestionPoolItem item, String poolHash) {
    TrainingPlanQuestionPool row = new TrainingPlanQuestionPool();
    row.setOrganizationId(plan.getOrganizationId());
    row.setTrainingPlanId(plan.getTrainingPlanId());
    row.setPlanContentId(content.getPlanContentId());
    row.setQuestionBankId(bank.getQuestionBankId());
    row.setQuestionId(item.getQuestionId());
    row.setQuestionVersionId(item.getQuestionVersionId());
    row.setQuestionType(item.getQuestionType());
    row.setDifficulty(item.getDifficulty());
    row.setKnowledgePointIdsJson(writeJson(item.getKnowledgePointIds()));
    row.setEvaluationMode(item.getEvaluationMode());
    row.setCandidateOrder(item.getCandidateOrder());
    row.setPoolHash(poolHash);
    return row;
  }

  private TrainingQuestionPoolItem toItem(TrainingPlanQuestionPool row) {
    TrainingQuestionPoolItem item = new TrainingQuestionPoolItem();
    item.setQuestionId(row.getQuestionId());
    item.setQuestionVersionId(row.getQuestionVersionId());
    item.setQuestionType(row.getQuestionType());
    item.setDifficulty(row.getDifficulty());
    try {
      item.setKnowledgePointIds(objectMapper.readValue(row.getKnowledgePointIdsJson(), new TypeReference<>() {
      }));
    } catch (Exception ex) {
      throw new BusinessException(HttpStatus.CONFLICT, CODE_POOL_INVALID, "冻结题池知识点快照无法解析");
    }
    item.setEvaluationMode(row.getEvaluationMode());
    item.setCandidateOrder(row.getCandidateOrder());
    return item;
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 8111, "题池快照序列化失败");
    }
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 8111, "题池快照哈希计算失败");
    }
  }

  private String canonicalHash(String json) {
    try {
      Object parsed = objectMapper.readValue(json, Object.class);
      return sha256(objectMapper.writeValueAsString(canonicalize(parsed)));
    } catch (Exception ex) {
      throw new IllegalArgumentException("question pool snapshot cannot be canonicalized", ex);
    }
  }

  private Object canonicalize(Object value) {
    if (value instanceof Map<?, ?> map) {
      TreeMap<String, Object> sorted = new TreeMap<>();
      map.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalize(item)));
      return sorted;
    }
    if (value instanceof List<?> list) {
      return list.stream().map(this::canonicalize).toList();
    }
    return value;
  }

  private BusinessException invalid(TrainingPlanContent content, String message) {
    return new BusinessException(HttpStatus.CONFLICT, CODE_POOL_INVALID,
                                 "计划内容 " + content.getPlanContentId() + " " + message);
  }

  public record FrozenPool(String poolHash, List<TrainingQuestionPoolItem> items) {
    public FrozenPool {
      items = List.copyOf(items);
    }
  }
}
