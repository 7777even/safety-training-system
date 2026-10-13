package com.sts.learner.vo;

import java.time.LocalDateTime;

/** 本人消息安全摘要，业务引用由调用端映射受控入口，不返回答案或任意URL。
 * @param recipientId 本人投递记录编号
 * @param messageId 持久化消息编号
 * @param kind 业务来源分类
 * @param title 消息标题
 * @param content 消息正文
 * @param resourceId 领域资源编号
 * @param parentId 上级业务编号
 * @param itemId 课程内容项编号
 * @param createTime 消息产生时间
 */
public record LearnerMessageVO(Long recipientId, Long messageId, String kind, String title, String content,
                               Long resourceId, Long parentId, Long itemId, LocalDateTime createTime) { }
