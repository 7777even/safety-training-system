package com.sts.question.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class QuestionUpdateRequest {
  @NotBlank private String questionType;
  @NotBlank private String stem;
  private String analysis;
  private String standardAnswer;
  @NotBlank private String difficulty;
  @NotBlank private String gradingMode;
  private Integer isUnorderedAnswerAllowed = 0;
  private Integer isCaseIgnored = 0;
  private Integer isFullHalfWidthIgnored = 0;
  private String versionNote;
  @NotNull private Integer version;
  private List<Long> knowledgePointIds;
  @Valid private List<QuestionOptionRequest> options;
  @Valid private List<QuestionBlankAnswerRequest> blankAnswers;
  @Valid private List<QuestionKeywordRequest> keywords;
}
