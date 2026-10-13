package com.sts.learner.service;
import com.sts.learner.mapper.LearnerNotificationSourceMapper;
import com.sts.training.service.TrainingPlanChangeService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
/** 驳回不得通知；批准变更继续使用既有消息事务。 */
class LearnerNotificationAspectTest {
  @Test void rejectionDoesNotNotifyAndApprovalDoes() throws Throwable {
    var tx=mock(TransactionTemplate.class);when(tx.execute(any())).thenAnswer(inv->((TransactionCallback<?>)inv.getArgument(0)).doInTransaction(mock(org.springframework.transaction.TransactionStatus.class)));
    var notifications=mock(LearnerNotificationService.class);var aspect=new LearnerNotificationAspect(tx,notifications,mock(LearnerNotificationSourceMapper.class));
    var call=mock(ProceedingJoinPoint.class);var signature=mock(Signature.class);when(signature.getName()).thenReturn("decide");when(call.getSignature()).thenReturn(signature);when(call.getTarget()).thenReturn(mock(TrainingPlanChangeService.class));
    when(call.getArgs()).thenReturn(new Object[]{5L,7L,false,"驳回",9L});aspect.notifyAfterSuccessfulChange(call);verifyNoInteractions(notifications);
    when(call.getArgs()).thenReturn(new Object[]{5L,7L,true,"批准",9L});aspect.notifyAfterSuccessfulChange(call);verify(notifications).businessChanged("training","decide",5L,null);
  }
}
