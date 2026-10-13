package com.sts.training.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.common.exception.BusinessException;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.vo.OfflineClassStatisticsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
/** 复用场次及录分，不建立线下答卷或签到。 */
@Service
@RequiredArgsConstructor
public class OfflineClassStatisticsService {
  private final TrainingOfflineSessionService sessions;
  private final TrainingPlanService plans;
  private final TrainingPlanContentMapper contentMapper;
  private final TrainingParticipantMapper participantMapper;
  private final TrainingOfflineScoreMapper scoreMapper;
  private final SysUserMapper userMapper;
  private final com.sts.paper.mapper.PaperVersionMapper paperVersionMapper;
  private final com.sts.paper.mapper.PaperMapper paperMapper;
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
  /** 验证场次父计划后才读取内容、人员和成绩。 */
  public OfflineClassStatisticsVO statistics(Long sessionId) {
    var session=sessions.get(sessionId);plans.get(session.getTrainingPlanId());
    var content=contentMapper.selectById(session.getPlanContentId());
    if(content==null || !Objects.equals(content.getPlanStageId(),session.getPlanStageId()))throw new BusinessException(HttpStatus.NOT_FOUND,8201,"场次培训内容不可见");
    var examContents=contentMapper.selectList(new LambdaQueryWrapper<TrainingPlanContent>().eq(TrainingPlanContent::getPlanStageId,session.getPlanStageId()).eq(TrainingPlanContent::getContentType,"EXAM"))
      .stream().map(this::examContent).toList();
    var participants=participantMapper.selectList(new LambdaQueryWrapper<TrainingParticipant>().eq(TrainingParticipant::getTrainingPlanId,session.getTrainingPlanId()));
    var ids=participants.stream().map(TrainingParticipant::getTrainingParticipantId).toList();
    var scores=ids.isEmpty()?List.<TrainingOfflineScore>of():scoreMapper.selectList(new LambdaQueryWrapper<TrainingOfflineScore>().eq(TrainingOfflineScore::getOfflineSessionId,sessionId).in(TrainingOfflineScore::getTrainingParticipantId,ids));
    var userIds=participants.stream().map(TrainingParticipant::getUserId).distinct().toList();
    var users=userIds.isEmpty()?Map.<Long,SysUser>of():userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId,userIds)).stream().collect(Collectors.toMap(SysUser::getUserId,Function.identity()));
    Map<String,List<TrainingOfflineScore>> assessments=new TreeMap<>();
    scores.forEach(s->assessments.computeIfAbsent(s.getScoreName(),k->new ArrayList<>()).add(s));
    List<OfflineClassStatisticsVO.Person> people=new ArrayList<>();
    for(var p:participants) {
      var rows=scores.stream().filter(s->Objects.equals(s.getTrainingParticipantId(),p.getTrainingParticipantId())).toList();
      String name=users.containsKey(p.getUserId())?users.get(p.getUserId()).getUserName():"历史学员";
      if(rows.isEmpty())people.add(new OfflineClassStatisticsVO.Person(p.getTrainingParticipantId(),p.getUserId(),name,null,null,null,null));
      else for(var score:rows)people.add(new OfflineClassStatisticsVO.Person(p.getTrainingParticipantId(),p.getUserId(),name,score.getScoreName(),score.getScore(),score.getMaxScore(),score.getScoredTime()));
    }
    long scored=scores.stream().filter(s->s.getScore()!=null).map(TrainingOfflineScore::getTrainingParticipantId).distinct().count();
    return new OfflineClassStatisticsVO(sessionId,session.getSessionName(),content.getContentName(),session.getLocation(),examContents,participants.size(),scored,participants.size()-scored,
      assessments.entrySet().stream().map(e->OfflineClassStatisticsCalculator.assessment(e.getKey(),participants.size(),e.getValue())).toList(),people);
  }
  /** 只展示已存发布快照中的试卷关联，不根据当前考试配置猜测。 */
  private OfflineClassStatisticsVO.ExamContent examContent(TrainingPlanContent content) {
    Long versionId=content.getResourceVersionId();
    if(content.getResourceSnapshotJson()!=null) {
      try {
        var snapshot=objectMapper.readTree(content.getResourceSnapshotJson());
        if(snapshot.hasNonNull("paperVersionId"))versionId=snapshot.get("paperVersionId").asLong();
      } catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new BusinessException(HttpStatus.CONFLICT,8167,"考试发布快照无法解析");}
    }
    var version=versionId==null?null:paperVersionMapper.selectById(versionId);
    var paper=version==null?null:paperMapper.selectById(version.getPaperId());
    return new OfflineClassStatisticsVO.ExamContent(content.getContentName(),versionId,paper==null?null:paper.getPaperName(),paper==null?null:version.getVersionNo());
  }
}
