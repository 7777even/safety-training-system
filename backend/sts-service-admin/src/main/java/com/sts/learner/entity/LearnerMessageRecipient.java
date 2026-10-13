package com.sts.learner.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/** 消息投递关系；不引入已读管理。 */
@Data @TableName("sts_common_message_recipient")
public class LearnerMessageRecipient {
  /** 投递主键，SSE按此游标读取已提交消息。 */
  @TableId(type = IdType.AUTO) private Long recipientId;
  /** 投递机构。 */
  private Long organizationId;
  /** 关联消息。 */
  private Long messageId;
  /** 实际接收用户。 */
  private Long userId;
}
