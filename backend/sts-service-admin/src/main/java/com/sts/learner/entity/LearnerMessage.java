package com.sts.learner.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 不可覆盖的站内消息正文；接收人与消息分开持久化。 */
@Data @TableName("sts_common_message")
public class LearnerMessage {
  /** 消息主键。 */
  @TableId(type = IdType.AUTO) private Long messageId;
  /** 所属机构，禁止跨租户收发。 */
  private Long organizationId;
  /** 业务事件唯一键。 */
  private String eventKey;
  /** COURSE/FORMAL_EXAM/COURSE_QUIZ/TRAINING/COURSE_CATALOG。 */
  private String kind;
  /** 消息标题。 */
  private String title;
  /** 提醒正文。 */
  private String content;
  /** 业务主引用。 */
  private Long resourceId;
  /** 父业务引用，考试为examId。 */
  private Long parentId;
  /** 课程测验内容项。 */
  private Long itemId;
  /** 服务端生成时间。 */
  private LocalDateTime createTime;
}
