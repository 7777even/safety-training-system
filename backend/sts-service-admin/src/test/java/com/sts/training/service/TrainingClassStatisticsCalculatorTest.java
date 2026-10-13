package com.sts.training.service;
import com.sts.training.vo.TrainingClassPersonVO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
/** 混合计划两类都展示，同一类别的重复内容不重复计人。 */
class TrainingClassStatisticsCalculatorTest {
  @Test void mixedPlanDoesNotDoubleTotalOrCountRepeatedContents() {
    var online=new TrainingClassPersonVO(1L,"计划",null,"未分配部门",7L,"学员","u7","ONLINE");
    var offline=new TrainingClassPersonVO(1L,"计划",null,"未分配部门",7L,"学员","u7","OFFLINE");
    var result=TrainingClassStatisticsCalculator.calculate(List.of(online,online,offline));
    assertThat(result.totalParticipantCount()).isEqualTo(1);assertThat(result.onlineParticipantCount()).isEqualTo(1);
    assertThat(result.offlineParticipantCount()).isEqualTo(1);assertThat(result.classes()).hasSize(2);
    assertThat(result.classes()).allSatisfy(c->assertThat(c.participantCount()).isEqualTo(1));
  }
}
