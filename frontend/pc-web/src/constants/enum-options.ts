import type { AudienceKind, AudiencePurpose, AudienceResolveMode, AudienceSubjectType } from '@/api/audience';
import type { CourseItemType, CoursePublishStatus, CourseType, EnrollmentType, CompletionRule, LearningStatus } from '@/api/course-operations';
import type { CertificateRule, ArchiveContent, ArchiveRewardPenalty, ArchiveScore, ArchiveSignInStatus } from '@/api/m4-outcome';
import type { OrganizationType } from '@/api/organization';
import type { GrantLevel } from '@/api/resource-grant';
import type { DeptType } from '@/api/system-dept';
import type { MemberType } from '@/api/system-user';
import type { RoleLevel } from '@/api/system-role';
import type { TrainingAttachmentType } from '@/api/training-process';
import { MEMBER_TYPE_ENTRIES } from '@shared/utils/member-type';

/** Contract 枚举的中文选项。 */
export interface EnumOption<T extends string> {
  label: string;
  value: T;
}
/** 将存储的 Contract value 转为界面中文文案。 */
export function getEnumLabel<T extends string>(value: T | null | undefined, options: readonly EnumOption<T>[]): string {
  return options.find((item) => item.value === value)?.label ?? '—';
}
export const COURSE_TYPE_OPTIONS = [
  { label: '必修', value: 'REQUIRED' },
  { label: '选修', value: 'ELECTIVE' }
] as const satisfies readonly EnumOption<CourseType>[];
export const ENROLLMENT_TYPE_OPTIONS = [
  { label: '指派报名', value: 'ASSIGNED' },
  { label: '开放报名', value: 'OPEN' }
] as const satisfies readonly EnumOption<EnrollmentType>[];
export const COMPLETION_RULE_OPTIONS = [
  { label: '全部必修内容', value: 'ALL_REQUIRED_ITEMS' },
  { label: '全部内容', value: 'ALL_ITEMS' }
] as const satisfies readonly EnumOption<CompletionRule>[];
export const COURSE_PUBLISH_STATUS_OPTIONS = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已结束', value: 'ENDED' },
  { label: '已取消', value: 'CANCELLED' }
] as const satisfies readonly EnumOption<CoursePublishStatus>[];
export const LEARNING_STATUS_OPTIONS = [
  { label: '未开始', value: 'NOT_STARTED' },
  { label: '学习中', value: 'LEARNING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已过期', value: 'EXPIRED' }
] as const satisfies readonly EnumOption<LearningStatus>[];
export const COURSE_ITEM_TYPE_OPTIONS = [
  { label: '素材', value: 'ASSET' },
  { label: '直播', value: 'LIVE' }
] as const satisfies readonly EnumOption<CourseItemType>[];
export const AUDIENCE_KIND_OPTIONS = [
  { label: '模板范围', value: 'TEMPLATE' },
  { label: '业务绑定范围', value: 'BOUND' }
] as const satisfies readonly EnumOption<AudienceKind>[];
export const AUDIENCE_PURPOSE_OPTIONS = [
  { label: '可见范围', value: 'VISIBILITY' },
  { label: '参与范围', value: 'PARTICIPANT' },
  { label: '评审范围', value: 'REVIEWER' },
  { label: '通知范围', value: 'NOTIFICATION' }
] as const satisfies readonly EnumOption<AudiencePurpose>[];
export const AUDIENCE_RESOLVE_MODE_OPTIONS = [
  { label: '动态解析', value: 'DYNAMIC' },
  { label: '冻结名单', value: 'FROZEN' }
] as const satisfies readonly EnumOption<AudienceResolveMode>[];
export const AUDIENCE_DECISION_OPTIONS = [
  {label: '包含', value: 'INCLUDE'},
  {label: '排除', value: 'EXCLUDE'}
] as const satisfies readonly EnumOption<'INCLUDE' | 'EXCLUDE'>[];
export const AUDIENCE_SUBJECT_TYPE_OPTIONS = [
  { label: '全部人员', value: 'ALL' },
  { label: '部门（含子部门）', value: 'DEPT' },
  { label: '标签', value: 'TAG' },
  { label: '岗位', value: 'POSITION' },
  { label: '人员', value: 'USER' },
  { label: '参与范围', value: 'AUDIENCE' }
] as const satisfies readonly EnumOption<AudienceSubjectType>[];
export const ORGANIZATION_TYPE_OPTIONS = [
  { label: '企业租户', value: 'ENTERPRISE' },
  { label: '监管平台', value: 'REGULATOR' }
] as const satisfies readonly EnumOption<OrganizationType>[];
export const GRANT_LEVEL_OPTIONS = [
  { label: '查看', value: 'VIEW' },
  { label: '使用', value: 'USE' },
  { label: '管理', value: 'MANAGE' }
] as const satisfies readonly EnumOption<GrantLevel>[];
export const ROLE_LEVEL_OPTIONS = [
  { label: '管理角色', value: 'ADMIN' },
  { label: '教师角色', value: 'TEACHER' },
  { label: '学员角色', value: 'STUDENT' }
] as const satisfies readonly EnumOption<RoleLevel>[];
/** 成员身份：本人资料只读展示与用户管理共用同一中文口径，字典本体在 `@shared/utils/member-type`（移动端同源）。 */
export const MEMBER_TYPE_OPTIONS: readonly EnumOption<MemberType>[] = MEMBER_TYPE_ENTRIES;
export const DEPT_TYPE_OPTIONS = [
  { label: '学校', value: 'SCHOOL' },
  { label: '学院', value: 'COLLEGE' },
  { label: '专业', value: 'MAJOR' },
  { label: '部门', value: 'DEPARTMENT' },
  { label: '班级', value: 'CLASS' },
  { label: '其他', value: 'OTHER' }
] as const satisfies readonly EnumOption<DeptType>[];
/** 证书来源类型：已签发证书展示与证书成品 SOURCE_TYPE 变量共用同一套中文口径。 */
export const CERTIFICATE_SOURCE_TYPE_OPTIONS = [
  { label: '课程', value: 'COURSE' },
  { label: '理论考试', value: 'EXAM' },
  { label: '培训计划', value: 'TRAINING' }
] as const satisfies readonly EnumOption<CertificateRule['sourceType']>[];
/**
 * 签发规则的来源类型口径：三类来源彼此独立，必须让管理员看清「独立对象」与「培训内引用对象」的区别。
 * 证书成品上的 SOURCE_TYPE 变量仍沿用上面的中文口径，两者不合并。
 */
export const CERTIFICATE_RULE_SOURCE_TYPE_OPTIONS = [
  { label: '独立课程', value: 'COURSE' },
  { label: '独立理论考试', value: 'EXAM' },
  { label: '培训计划', value: 'TRAINING' }
] as const satisfies readonly EnumOption<CertificateRule['sourceType']>[];
/** 培训内容类型：档案与过程记录都要把 COURSE/EXAM/QUESTION_BANK/OFFLINE 显示为中文，不散落本地字典。 */
export const TRAINING_CONTENT_TYPE_OPTIONS = [
  { label: '课程', value: 'COURSE' },
  { label: '理论考试', value: 'EXAM' },
  { label: '题库练习', value: 'QUESTION_BANK' },
  { label: '线下培训', value: 'OFFLINE' }
] as const satisfies readonly EnumOption<ArchiveContent['contentType']>[];
/** 参训人结果状态：分母只统计 PASSED/FAILED，INCOMPLETE 单列，三态必须穷举。 */
export const TRAINING_RESULT_STATUS_OPTIONS = [
  { label: '合格', value: 'PASSED' },
  { label: '不合格', value: 'FAILED' },
  { label: '结果待补全', value: 'INCOMPLETE' }
] as const satisfies readonly EnumOption<NonNullable<ArchiveScore['resultStatus']>>[];
/** 培训资料类型，枚举域与 TrainingAttachmentVO 一致；奖惩佐证已收口到奖惩记录，不在此列。 */
export const TRAINING_ATTACHMENT_TYPE_OPTIONS = [
  { label: '一般资料', value: 'GENERAL' },
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' },
  { label: '评价佐证', value: 'EVALUATION' }
] as const satisfies readonly EnumOption<TrainingAttachmentType>[];
/** 奖惩记录类型。 */
export const TRAINING_REWARD_RECORD_TYPE_OPTIONS = [
  { label: '奖励', value: 'REWARD' },
  { label: '处罚', value: 'PENALTY' }
] as const satisfies readonly EnumOption<ArchiveRewardPenalty['recordType']>[];
/**
 * 档案签到三态：由「教师是否已录入线下成绩」派生的查询期口径，不落库。
 * 计划不含线下内容时为 NOT_APPLICABLE，必须与「未记录」区分展示。
 */
export const ARCHIVE_SIGN_IN_STATUS_OPTIONS = [
  { label: '已到场', value: 'SIGNED' },
  { label: '未记录', value: 'NOT_RECORDED' },
  { label: '不适用', value: 'NOT_APPLICABLE' }
] as const satisfies readonly EnumOption<ArchiveSignInStatus>[];
