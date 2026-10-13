import type { ApiResponse } from '@shared/types';
import type { PersonPickerItem } from './person-picker';
import { request } from './request';

/** PersonProfileRequest：对应已确认人员分类契约。 */
export interface PersonProfileRequest {
  /** 出生日期，年龄按服务端当前日期计算 */
  birthDate?: string | null;
  /** 性别：男、女、其他 */
  gender?: 'MALE' | 'FEMALE' | 'OTHER' | null;
  /** 职业，可清空 */
  occupation?: string | null;
  /** 学历，可清空 */
  education?: string | null;
  /** 专业，可清空 */
  major?: string | null;
  /** 从业经历，可清空 */
  workExperience?: string | null;
  /** 专长，可清空 */
  specialty?: string | null;
  /** 职业资格，可清空 */
  qualification?: string | null;
  /** 人工录入的人员奖惩说明，可清空 */
  rewardPenaltyNote?: string | null;
  /** 工作内容，可清空 */
  workContent?: string | null;
  /** 工作技能，可清空 */
  workSkills?: string | null;
  /** 乐观锁版本，首次保存为0 */
  version: number;
}

/** PersonProfileVO：对应已确认人员分类契约。 */
export interface PersonProfileVO {
  /** 出生日期，年龄按服务端当前日期计算 */
  birthDate?: string | null;
  /** 性别：男、女、其他 */
  gender?: 'MALE' | 'FEMALE' | 'OTHER' | null;
  /** 职业，可清空 */
  occupation?: string | null;
  /** 学历，可清空 */
  education?: string | null;
  /** 专业，可清空 */
  major?: string | null;
  /** 从业经历，可清空 */
  workExperience?: string | null;
  /** 专长，可清空 */
  specialty?: string | null;
  /** 职业资格，可清空 */
  qualification?: string | null;
  /** 人工录入的人员奖惩说明，可清空 */
  rewardPenaltyNote?: string | null;
  /** 工作内容，可清空 */
  workContent?: string | null;
  /** 工作技能，可清空 */
  workSkills?: string | null;
  /** 乐观锁版本，首次保存为0 */
  version: number;
  /** 人员ID */
  userId: number;
}

/** 不同条件交集；空条件代表当前租户全部启用人员。不会修改已下发名单。 */
export interface PersonClassificationCondition {
  /** 部门（包含下级），同字段并集 */
  deptIds?: number[];
  /** 岗位，同字段并集 */
  positionIds?: number[];
  /** 标签，同字段并集 */
  tagIds?: number[];
  /** 性别，同字段并集 */
  genders?: string[];
  /** 学历，同字段并集 */
  educations?: string[];
  /** 年龄范围，含边界；缺失出生日期不匹配 */
  minAge?: number;
  /** 年龄范围，含边界；缺失出生日期不匹配 */
  maxAge?: number;
  /** 人员关键词（姓名/工号） */
  keyword?: string;
  /** 职业，可清空关键词 */
  occupation?: string;
  /** 专业，可清空关键词 */
  major?: string;
  /** 从业经历，可清空关键词 */
  workExperience?: string;
  /** 专长，可清空关键词 */
  specialty?: string;
  /** 职业资格，可清空关键词 */
  qualification?: string;
  /** 人工录入的人员奖惩说明，可清空关键词 */
  rewardPenaltyNote?: string;
  /** 工作内容，可清空关键词 */
  workContent?: string;
  /** 工作技能，可清空关键词 */
  workSkills?: string;
  /** 按参加的培训计划筛选 */
  trainingPlanId?: number;
  /** 培训结果状态，必须与计划一起指定 */
  resultStatuses?: ('INCOMPLETE' | 'PASSED' | 'FAILED')[];
}

/** PersonGroupRequest：对应已确认人员分类契约。 */
export interface PersonGroupRequest {
  /** 用户组名称 */
  groupName: string;
  /** 状态：0停用、1启用 */
  status: 0 | 1;
  /** 已确认固定分类条件。 */
  conditions: PersonClassificationCondition;
  /** 更新时必填版本 */
  version?: number;
}

/** PersonGroupVO：对应已确认人员分类契约。 */
export interface PersonGroupVO {
  /** 用户组名称 */
  groupName?: string;
  /** 状态：0停用、1启用 */
  status?: 0 | 1;
  /** 已确认固定分类条件。 */
  conditions?: PersonClassificationCondition;
  /** 更新时必填版本 */
  version?: number;
  /** 用户组ID */
  groupId?: number;
}

/** PersonTrainingFactVO：对应已确认人员分类契约。 */
export interface PersonTrainingFactVO {
  /** 培训计划ID */
  trainingPlanId?: number;
  /** 培训计划名称 */
  planName?: string;
  /** 培训结果状态 */
  resultStatus?: string;
  /** 完成时间 */
  completedTime?: string | null;
  /** 已获得线上学时 */
  onlineHours?: number;
  /** 已获得线下学时 */
  offlineHours?: number;
  /** 已登记培训奖惩标题，仅展示有效记录 */
  rewardPenaltyTitles?: string | null;
}

/** 校验统一响应，失败时不显示成功。 */
function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new Error(response.message || '请求失败');
  return response.data;
}
/** 查询全部组配置，停用组仅用于管理回显。 */
export async function getPersonGroups(): Promise<PersonGroupVO[]> {
  return unwrap(await request<ApiResponse<PersonGroupVO[]>>({ url: '/api/person/groups', method: 'GET' }));
}
/** 当前条件人员预览，不产生任务。 */
export async function previewPersonGroup(conditions: PersonClassificationCondition): Promise<PersonPickerItem[]> {
  return unwrap(await request<ApiResponse<PersonPickerItem[]>>({ url: '/api/person/groups/preview', method: 'POST', data: conditions }));
}
/** 保存条件组；更新透传版本。 */
export async function savePersonGroup(group: PersonGroupRequest & { groupId?: number }): Promise<number | null> {
  const { groupId, ...data } = group;
  return unwrap(
    await request<ApiResponse<number | null>>({
      url: groupId ? `/api/person/groups/${groupId}` : '/api/person/groups',
      method: groupId ? 'PUT' : 'POST',
      data
    })
  );
}
/** 获取人工特征。 */
export async function getPersonProfile(userId: number): Promise<PersonProfileVO> {
  return unwrap(await request<ApiResponse<PersonProfileVO>>({ url: `/api/person/profiles/${userId}`, method: 'GET' }));
}
/** 保存包括显式清空值的人工特征。 */
export async function savePersonProfile(userId: number, data: PersonProfileRequest): Promise<void> {
  const payload = Object.fromEntries(Object.entries(data).filter(([key]) => key !== 'userId'));
  unwrap(await request<ApiResponse<null>>({ url: `/api/person/profiles/${userId}`, method: 'PUT', data: payload }));
}
/** 培训事实只读查询。 */
export async function getPersonTrainingFacts(userId: number): Promise<PersonTrainingFactVO[]> {
  return unwrap(await request<ApiResponse<PersonTrainingFactVO[]>>({ url: `/api/person/profiles/${userId}/training-facts`, method: 'GET' }));
}
