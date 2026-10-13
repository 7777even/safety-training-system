package com.sts.training.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.*;
import com.sts.admin.mapper.*;
import com.sts.common.api.PageResult;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
/** 在当前租户可见计划事实内构建部门班级，不创建班级表。 */
@Service
@RequiredArgsConstructor
public class TrainingClassStatisticsService {
  private final TrainingPlanService plans;
  private final TrainingPlanMapper planMapper;
  private final TrainingPlanStageMapper stageMapper;
  private final TrainingPlanContentMapper contentMapper;
  private final TrainingParticipantMapper participantMapper;
  private final SysUserMapper userMapper;
  private final SysDeptMapper deptMapper;
  /** 汇总与人员下钻共享同一事实集合。 */
  public TrainingClassStatisticsVO statistics(Long planId) {return TrainingClassStatisticsCalculator.calculate(facts(planId));}
  /** 部门为空时通过 unassignedOnly 显式筛选，避免空值表示所有部门。 */
  public PageResult<TrainingClassPersonVO> people(Long planId,Long deptId,boolean unassignedOnly,String mode,String keyword,int pageNum,int pageSize) {
    var rows=facts(planId).stream().filter(p->deptId==null||Objects.equals(deptId,p.deptId())).filter(p->!unassignedOnly||p.deptId()==null)
      .filter(p->mode==null||mode.equals(p.mode())).filter(p->keyword==null||keyword.isBlank()||(p.learnerName()+" "+p.learnerCode()).contains(keyword)).distinct().toList();
    long start=(long)(pageNum-1)*pageSize;return new PageResult<>(start>=rows.size()?List.of():rows.subList((int)start,(int)Math.min(rows.size(),start+pageSize)),rows.size());
  }
  /** 先限定计划和内容，再读取名单及其部门。 */
  private List<TrainingClassPersonVO> facts(Long planId) {
    if(planId!=null)plans.get(planId);
    var visible=planMapper.selectList(new LambdaQueryWrapper<TrainingPlan>().eq(planId!=null,TrainingPlan::getTrainingPlanId,planId));
    if(visible.isEmpty())return List.of();
    var planIds=visible.stream().map(TrainingPlan::getTrainingPlanId).toList();
    var planById=visible.stream().collect(Collectors.toMap(TrainingPlan::getTrainingPlanId,Function.identity()));
    var stages=stageMapper.selectList(new LambdaQueryWrapper<TrainingPlanStage>().in(TrainingPlanStage::getTrainingPlanId,planIds));
    var stageById=stages.stream().collect(Collectors.toMap(TrainingPlanStage::getPlanStageId,TrainingPlanStage::getTrainingPlanId));
    Map<Long,Set<String>> modes=new HashMap<>();
    if(!stages.isEmpty())for(var c:contentMapper.selectList(new LambdaQueryWrapper<TrainingPlanContent>().in(TrainingPlanContent::getPlanStageId,stageById.keySet()))) {
      String mode="OFFLINE".equals(c.getContentType())?"OFFLINE":Set.of("COURSE","EXAM","QUESTION_BANK").contains(c.getContentType())?"ONLINE":null;
      if(mode!=null)modes.computeIfAbsent(stageById.get(c.getPlanStageId()),k->new LinkedHashSet<>()).add(mode);
    }
    var participants=participantMapper.selectList(new LambdaQueryWrapper<TrainingParticipant>().in(TrainingParticipant::getTrainingPlanId,planIds));
    if(participants.isEmpty())return List.of();
    var users=userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId,participants.stream().map(TrainingParticipant::getUserId).distinct().toList())).stream().collect(Collectors.toMap(SysUser::getUserId,Function.identity()));
    var deptIds=users.values().stream().map(SysUser::getDeptId).filter(Objects::nonNull).distinct().toList();
    var depts=deptIds.isEmpty()?Map.<Long,SysDept>of():deptMapper.selectList(new LambdaQueryWrapper<SysDept>().in(SysDept::getDeptId,deptIds)).stream().collect(Collectors.toMap(SysDept::getDeptId,Function.identity()));
    List<TrainingClassPersonVO> rows=new ArrayList<>();
    for(var p:participants) {
      var user=users.get(p.getUserId());Long deptId=user==null?null:user.getDeptId();
      for(String mode:modes.getOrDefault(p.getTrainingPlanId(),Set.of()))rows.add(new TrainingClassPersonVO(p.getTrainingPlanId(),planById.get(p.getTrainingPlanId()).getPlanName(),deptId,deptId==null?"未分配部门":depts.containsKey(deptId)?depts.get(deptId).getDeptName():"历史部门",p.getUserId(),user==null?"历史学员":user.getUserName(),user==null?"":user.getUserCode(),mode));
    }
    return rows;
  }
}
