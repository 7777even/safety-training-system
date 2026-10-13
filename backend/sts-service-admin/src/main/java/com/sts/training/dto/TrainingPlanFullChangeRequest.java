package com.sts.training.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.sts.person.dto.PersonSelectionRequest;
import lombok.Data;
import java.util.List;
/** 未安排执行的计划完整替换；人员在提交时展开并冻结。 */
@Data public class TrainingPlanFullChangeRequest {
  /** 基础范围。 */ @Valid @NotNull private TrainingPlanUpdateRequest plan;
  /** 完整课表。 */ @Valid @NotEmpty @Size(max=100) private List<TrainingPlanStageRequest> stages;
  /** 完整评价绑定。 */ @Valid @NotNull @Size(max=2) private List<TrainingPlanEvaluationFormItemRequest> forms;
  /** 待展开的人员条件。 */ @Valid @NotNull private PersonSelectionRequest participantSelection;
  /** 提交时冻结的人员，客户端值会被覆盖。 */ private List<Long> participantUserIds;
  /** 提交时年度文件快照，客户端值在提交时覆盖。 */ private com.sts.training.vo.TrainingAnnualPlanVO annualSnapshot;
  /** 可选年度关联变更，不传则保留原关联。 */ @Valid private TrainingAnnualPlanBindRequest annual;
}
