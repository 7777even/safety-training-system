package com.sts.exam.dto;
import com.sts.person.dto.PersonSelectionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.util.List;
/** 评卷人可用统一范围或固定名单，二者互斥；空固定名单表示清空。 */
@Data
public class ExamReviewersReplaceRequest {
  /** 兼容已有固定名单调用。 */
  @Size(max=1000) private List<@Positive Long> reviewerUserIds;
  /** 当前条件展开为名单快照，后续组变化不自动增删。 */
  @Valid private PersonSelectionRequest selection;
  /** 实际展开后记录范围来源，不允许客户端伪造任意值。 */
  @Pattern(regexp="USER|DEPT|POSITION|GROUP|ALL") private String assignmentSource="USER";
}
