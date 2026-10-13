package com.sts.exam.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.exam.entity.AssessmentExecutionQuestionPool;
import com.sts.exam.mapper.AssessmentExecutionQuestionPoolMapper;
import com.sts.question.entity.QuestionKnowledge;
import com.sts.question.mapper.QuestionKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
/** 新作答优先使用执行题池的冻结知识点，否则冻结开考时的关联。 */
@Service
@RequiredArgsConstructor
public class AttemptKnowledgeSnapshotService {
  private final AssessmentExecutionQuestionPoolMapper poolMapper;
  private final QuestionKnowledgeMapper relationMapper;
  private final ObjectMapper objectMapper;
  private final com.sts.exam.mapper.AssessmentExecutionMapper executionMapper;
  /** 空数组表示已记录但无知识点，null 仅保留历史未记录语义。 */
  public String snapshot(Long executionId,Long questionId,Long questionVersionId) {
    var rows=poolMapper.selectList(new LambdaQueryWrapper<AssessmentExecutionQuestionPool>()
      .eq(AssessmentExecutionQuestionPool::getAssessmentExecutionId,executionId).eq(AssessmentExecutionQuestionPool::getQuestionId,questionId).eq(AssessmentExecutionQuestionPool::getQuestionVersionId,questionVersionId));
    if(!rows.isEmpty()) return rows.get(0).getKnowledgePointIdsJson();
    var frozen=CourseKnowledgeSnapshotService.read(executionMapper.selectById(executionId),objectMapper);
    var snapshot=frozen.stream().filter(q->java.util.Objects.equals(questionId,q.questionId())&&java.util.Objects.equals(questionVersionId,q.questionVersionId())).findFirst();
    if(snapshot.isPresent())return objectMapper.valueToTree(snapshot.get().knowledgePointIds()).toString();
    var ids=relationMapper.selectList(new LambdaQueryWrapper<QuestionKnowledge>().eq(QuestionKnowledge::getQuestionId,questionId))
      .stream().map(QuestionKnowledge::getKnowledgePointId).distinct().sorted().toList();
    return objectMapper.valueToTree(ids).toString();
  }
}
