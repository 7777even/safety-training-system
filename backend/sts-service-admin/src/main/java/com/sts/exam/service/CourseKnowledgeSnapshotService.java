package com.sts.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.exception.BusinessException;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.paper.entity.PaperVersionQuestion;
import com.sts.paper.mapper.PaperVersionQuestionMapper;
import com.sts.question.entity.*;
import com.sts.question.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.*;

/** 新课程执行创建时冻结知识点，历史执行不根据当前关联补写。 */
@Service
@RequiredArgsConstructor
public class CourseKnowledgeSnapshotService {
  private final PaperVersionQuestionMapper paperMapper;
  private final QuestionMapper questionMapper;
  private final QuestionVersionMapper versionMapper;
  private final QuestionKnowledgeMapper relationMapper;

  /** 一个冻结题目版本及其当时知识点；空数组表示明确无关联。 */
  public record Snapshot(Long questionId,Long questionVersionId,List<Long> knowledgePointIds) {}

  /** 复用现有执行规则 JSON 保存快照，不改变培训专用题池表含义。 */
  public List<Snapshot> freeze(Long paperVersionId,Long bankId) {
    Map<Long,Long> versions=new LinkedHashMap<>();
    if(paperVersionId!=null) {
      for(var q:paperMapper.selectList(new LambdaQueryWrapper<PaperVersionQuestion>().eq(PaperVersionQuestion::getPaperVersionId,paperVersionId).orderByAsc(PaperVersionQuestion::getSortOrder)))versions.put(q.getQuestionId(),q.getQuestionVersionId());
    } else if(bankId!=null) {
      var questions=questionMapper.selectList(new LambdaQueryWrapper<Question>().eq(Question::getQuestionBankId,bankId).eq(Question::getStatus,1));
      if(!questions.isEmpty())for(var v:versionMapper.selectList(new LambdaQueryWrapper<QuestionVersion>().in(QuestionVersion::getQuestionId,questions.stream().map(Question::getQuestionId).toList()).eq(QuestionVersion::getPublishedStatus,"PUBLISHED").orderByDesc(QuestionVersion::getVersionNo)))versions.putIfAbsent(v.getQuestionId(),v.getQuestionVersionId());
    }
    if(versions.isEmpty())return List.of();
    Map<Long,List<Long>> knowledge=new HashMap<>();
    for(var rel:relationMapper.selectList(new LambdaQueryWrapper<QuestionKnowledge>().in(QuestionKnowledge::getQuestionId,versions.keySet())))knowledge.computeIfAbsent(rel.getQuestionId(),k->new ArrayList<>()).add(rel.getKnowledgePointId());
    return versions.entrySet().stream().map(e->new Snapshot(e.getKey(),e.getValue(),knowledge.getOrDefault(e.getKey(),List.of()).stream().distinct().sorted().toList())).toList();
  }

  /** 只读取已保存快照，缺失字段保留历史未知语义。 */
  public static List<Snapshot> read(AssessmentExecution execution,ObjectMapper mapper) {
    if(execution==null)return List.of();
    // policy 是非持久化视图，必须先校验并解码数据库完整快照。
    new AssessmentExecutionSnapshotCodec(mapper).decodeAndVerify(execution);
    try {
      var node=mapper.readTree(execution.getPolicySnapshotJson()).get("knowledgePointSnapshots");
      if(node==null)return List.of();
      return mapper.convertValue(node,mapper.getTypeFactory().constructCollectionType(List.class,Snapshot.class));
    } catch(Exception e) {throw new BusinessException(HttpStatus.CONFLICT,8167,"课程知识点冻结快照无法解析");}
  }
}
