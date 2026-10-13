package com.sts.learner.mapper;

import com.sts.learner.entity.LearnerMessage;
import org.apache.ibatis.annotations.*;
import com.sts.learner.vo.LearnerMessageVO;
import java.util.List;

/** 消息落库和本人查询均显式传入机构、用户，不使用客户端身份。 */
@Mapper
public interface LearnerMessageMapper {
  /** 已存在事件只回取主键，正文不覆盖。 */
  @Insert("""
    INSERT INTO sts_common_message(organization_id,event_key,kind,title,content,resource_id,parent_id,item_id)
    VALUES(#{organizationId},#{eventKey},#{kind},#{title},#{content},#{resourceId},#{parentId},#{itemId})
    ON DUPLICATE KEY UPDATE message_id=LAST_INSERT_ID(message_id)
    """)
  @Options(useGeneratedKeys = true, keyProperty = "messageId")
  int save(LearnerMessage message);

  /** 同一事件仅投递给同一用户一次。 */
  @Insert("""
    INSERT INTO sts_common_message_recipient(organization_id,message_id,user_id)
    VALUES(#{org},#{message},#{user}) ON DUPLICATE KEY UPDATE message_id=VALUES(message_id)
    """)
  int addRecipient(@Param("org") Long org, @Param("message") Long message, @Param("user") Long user);

  /** 本人消息按投递时间分页，不依赖资源仍可访问。 */
  @Select("""
    SELECT r.recipient_id,m.message_id,m.kind,m.title,m.content,m.resource_id,m.parent_id,m.item_id,m.create_time
    FROM sts_common_message_recipient r JOIN sts_common_message m
      ON m.message_id=r.message_id AND m.organization_id=r.organization_id
    WHERE r.organization_id=#{org} AND r.user_id=#{user}
    ORDER BY r.recipient_id DESC LIMIT #{size} OFFSET #{offset}
    """)
  List<LearnerMessageVO> page(@Param("org") Long org, @Param("user") Long user, @Param("size") int size, @Param("offset") long offset);

  /** 本人消息总数。 */
  @Select("SELECT COUNT(*) FROM sts_common_message_recipient WHERE organization_id=#{org} AND user_id=#{user}")
  long count(@Param("org") Long org, @Param("user") Long user);

  /** 一个机构内分批统计在线接收人，避免每连接发起一次查询。 */
  record Count(Long userId, Long total) { }
  @Select("""
    <script>
    SELECT user_id,COUNT(*) total FROM sts_common_message_recipient
    WHERE organization_id=#{org} AND user_id IN
      <foreach collection="users" item="user" open="(" separator="," close=")">#{user}</foreach>
    GROUP BY user_id
    </script>
    """)
  List<Count> counts(@Param("org") Long org, @Param("users") List<Long> users);
}
