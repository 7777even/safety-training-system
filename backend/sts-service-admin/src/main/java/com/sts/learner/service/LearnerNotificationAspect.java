package com.sts.learner.service;

import com.sts.admin.tenant.TenantContext;
import com.sts.learner.mapper.LearnerNotificationSourceMapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 明确登记的业务写入口与消息共用外层事务；原服务和内部调用方无需改变接口。 */
@Aspect @Component @Order(0) @RequiredArgsConstructor
public class LearnerNotificationAspect {
  /** 原业务与消息必须一起提交或回滚。 */
  private final TransactionTemplate transactions;
  /** 事件落库编排。 */
  private final LearnerNotificationService notifications;
  /** 课程状态用于跳过幂等发布。 */
  private final LearnerNotificationSourceMapper sources;

  /** 仅拦截已确认的安排、发布和培训状态变化，避免把普通编辑当通知。 */
  @Around("execution(* com.sts.course.service.CourseEnrollmentServiceImpl.assign(..)) || "
    + "execution(* com.sts.course.service.CourseOfferingServiceImpl.publishOffering(..)) || "
    + "execution(* com.sts.course.service.CoursePublishServiceImpl.publish(..)) || "
    + "execution(* com.sts.course.service.CoursePublishServiceImpl.offline(..)) || "
    + "execution(* com.sts.exam.service.ExamServiceImpl.bindSelection(..)) || "
    + "execution(* com.sts.exam.service.ExamServiceImpl.publish(..)) || "
    + "execution(* com.sts.training.service.TrainingPlanServiceImpl.publish(..)) || "
    + "execution(* com.sts.training.service.TrainingPlanChangeService.decide(..)) || "
    + "execution(* com.sts.training.service.TrainingPlanLifecycleServiceImpl.start(..)) || "
    + "execution(* com.sts.training.service.TrainingPlanLifecycleServiceImpl.cancel(..))")
  public Object notifyAfterSuccessfulChange(ProceedingJoinPoint call) {
    Long id = (Long) call.getArgs()[0];
    String type = call.getTarget().getClass().getSimpleName(), action = call.getSignature().getName();
    String family = type.equals("CoursePublishServiceImpl") ? "catalog" : type.startsWith("Course") ? "course"
      : type.startsWith("Exam") ? "exam" : "training";
    return transactions.execute(status -> {
      String previous = family.equals("catalog") ? sources.courseState(TenantContext.requireTenantId(), id) : null;
      Object result;
      try { result = call.proceed(); }
      catch (RuntimeException | Error e) { throw e; }
      catch (Throwable e) { throw new IllegalStateException("业务执行失败", e); }
      // 驳回不通知；批准后沿用具体名单和已有唯一事件键，新增人员获得安排消息。
      if (!type.equals("TrainingPlanChangeService") || Boolean.TRUE.equals(call.getArgs()[2]))
        notifications.businessChanged(family, action, id, previous);
      return result;
    });
  }
}
