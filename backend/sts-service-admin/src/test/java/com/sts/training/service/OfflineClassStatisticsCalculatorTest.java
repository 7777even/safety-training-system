package com.sts.training.service;
import com.sts.training.entity.TrainingOfflineScore;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
/** 不同满分归一化，未评分不按零分参与均值。 */
class OfflineClassStatisticsCalculatorTest {
  @Test void scoresAreNormalizedAndUnscoredPeopleRemainSeparate() {
    var a=score(1L,"50","50");var b=score(2L,"100","100");
    var result=OfflineClassStatisticsCalculator.assessment("课堂考核",3,List.of(a,b));
    assertThat(result.scoredCount()).isEqualTo(2);assertThat(result.unscoredCount()).isEqualTo(1);
    assertThat(result.averagePercent()).isEqualByComparingTo("100");
    assertThat(result.scoreBands().get(4).count()).isEqualTo(2);
  }
  static TrainingOfflineScore score(long id,String score,String max) {
    var s=new TrainingOfflineScore();s.setTrainingParticipantId(id);s.setScore(new BigDecimal(score));s.setMaxScore(new BigDecimal(max));return s;
  }
}
