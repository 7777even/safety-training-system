package com.sts.training.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.common.api.PageResult;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;
/** 培训记录统计，GET 不调用 synchronize，不写投影或冻结结果。 */
@Service
@RequiredArgsConstructor
public class TrainingStatisticsService {
  private final TrainingPlanService plans;
  private final TrainingPlanMapper planMapper;
  private final TrainingParticipantMapper participantMapper;
  private final TrainingPlanStageMapper stageMapper;
  private final TrainingPlanContentMapper contentMapper;
  private final SysUserMapper userMapper;
  private final TrainingResultSyncService results;
  /** 先分页可见计划，再读取各计划来源事实。 */
  public PageResult<TrainingPlanStatisticsVO> statistics(String keyword,int pageNum,int pageSize) {
    var plans=planMapper.selectList(new LambdaQueryWrapper<TrainingPlan>().like(keyword!=null&&!keyword.isBlank(),TrainingPlan::getPlanName,keyword).orderByDesc(TrainingPlan::getTrainingPlanId));
    var rows=slice(plans,pageNum,pageSize).stream().map(p->{var people=peopleFacts(p.getTrainingPlanId());long completed=people.stream().filter(x->completed(x.resultStatus())).count();long failed=people.stream().filter(x->"FAILED".equals(x.resultStatus())).count();
      return new TrainingPlanStatisticsVO(p.getTrainingPlanId(),p.getPlanName(),p.getPublishStatus(),people.size(),completed,failed,people.size()-completed-failed,percent(completed,people.size()));}).toList();
    return new PageResult<>(rows,plans.size());
  }
  /** 仅查看完成人员时保留计划及关键字范围。 */
  public PageResult<TrainingProgressPersonVO> people(Long planId,boolean completedOnly,String keyword,int pageNum,int pageSize) {
    var rows=peopleFacts(planId).stream().filter(p->!completedOnly||completed(p.resultStatus())).filter(p->keyword==null||keyword.isBlank()||(p.learnerName()+" "+p.learnerCode()).contains(keyword)).toList();
    return new PageResult<>(slice(rows,pageNum,pageSize),rows.size());
  }
  /** 沿用 PASSED／COMPLETED，不把失败或未完成算入完成。 */
  static boolean completed(String status) {return "PASSED".equals(status)||"COMPLETED".equals(status);}
  /** 零分母返回零，进度不能生成 NaN。 */
  static BigDecimal percent(long value,long total) {return total==0?BigDecimal.ZERO:BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP);}
  /** 安全分页，避免大页码溢出。 */
  private static <T> List<T> slice(List<T> rows,int pageNum,int pageSize) {long start=(long)(pageNum-1)*pageSize;return start>=rows.size()?List.of():rows.subList((int)start,(int)Math.min(rows.size(),start+pageSize));}
  /** 已冻结记录由既有快照解析器读取，执行中通过只读来源接口推导。 */
  private List<TrainingProgressPersonVO> peopleFacts(Long planId) {
    plans.get(planId);
    var participants=participantMapper.selectList(new LambdaQueryWrapper<TrainingParticipant>().eq(TrainingParticipant::getTrainingPlanId,planId));
    if(participants.isEmpty())return List.of();
    var stages=stageMapper.selectList(new LambdaQueryWrapper<TrainingPlanStage>().eq(TrainingPlanStage::getTrainingPlanId,planId));
    var required=stages.isEmpty()?Set.<Long>of():contentMapper.selectList(new LambdaQueryWrapper<TrainingPlanContent>().in(TrainingPlanContent::getPlanStageId,stages.stream().map(TrainingPlanStage::getPlanStageId).toList()).eq(TrainingPlanContent::getIsRequired,1)).stream().map(TrainingPlanContent::getPlanContentId).collect(Collectors.toSet());
    var users=userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId,participants.stream().map(TrainingParticipant::getUserId).distinct().toList())).stream().collect(Collectors.toMap(SysUser::getUserId,Function.identity()));
    return participants.stream().map(p->{var result=results.getCurrentResults(planId,p.getTrainingParticipantId());var user=users.get(p.getUserId());var items=result.getItems()==null?List.<TrainingParticipantResultItemVO>of():result.getItems();long count=items.stream().filter(i->required.contains(i.getPlanContentId())&&"COMPLETED".equals(i.getResultStatus())).count();
      return new TrainingProgressPersonVO(p.getTrainingParticipantId(),p.getUserId(),user==null?"历史学员":user.getUserName(),user==null?"":user.getUserCode(),result.getResultStatus(),required.size(),count,required.isEmpty()?(completed(result.getResultStatus())?BigDecimal.valueOf(100):BigDecimal.ZERO):percent(count,required.size()),result.getOnlineHours(),result.getOfflineHours(),result.getResultFinalizedTime()!=null,items);}).toList();
  }
}
