package com.sts.question.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuestionVersionVO {
  private Long questionVersionId;
  private Long questionId;
  private Integer versionNo;
  private String questionType;
  private String stem;
  private String analysis;
  private String standardAnswer;
  private String difficulty;
  private String gradingMode;
  private Integer isUnorderedAnswerAllowed;
  private Integer isCaseIgnored;
  private Integer isFullHalfWidthIgnored;
  private String publishedStatus;
  private Long publishedBy;
  private LocalDateTime publishedTime;
  private String versionNote;
  private List<QuestionOptionVO> options;
  private List<com.sts.question.dto.QuestionBlankAnswerRequest> blankAnswers;
  private List<com.sts.question.dto.QuestionKeywordRequest> keywords;
  private List<QuestionAssetVO> assets;
}
