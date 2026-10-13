package com.sts.training.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class TrainingTeacherTaskDetailVO extends TrainingTeacherTaskVO {
  private List<TrainingTeacherRosterItemVO> roster;
  private List<String> availableActions;
}
