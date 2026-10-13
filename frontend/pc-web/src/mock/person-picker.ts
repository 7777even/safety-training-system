import type { PersonPickerItem, PersonPickerQuery, PersonPickerPage } from '@/api/person-picker';
import type { ApiResponse } from '@shared/types';

/**
 * TODO replace real api：后端人员选择接口联调通过后删除本文件及 API Mock 分支。
 * 数据只模拟 `/api/common/person-picker` 已声明的返回字段和过滤规则。
 */
const people: Array<PersonPickerItem & { tagIds: number[]; positionIds: number[] }> = [
  {
    userId: 1001,
    userCode: 'zhangsan',
    userName: '张三',
    organizationId: 1,
    deptId: 10,
    deptName: '安全管理部',
    memberType: 'INTERNAL',
    jobTitle: '安全主管',
    status: 1,
    tagIds: [1],
    positionIds: [1]
  },
  {
    userId: 1002,
    userCode: 'lisi',
    userName: '李四',
    organizationId: 1,
    deptId: 11,
    deptName: '安全培训组',
    memberType: 'INTERNAL',
    jobTitle: '培训专员',
    status: 1,
    tagIds: [2],
    positionIds: [2]
  },
  {
    userId: 1003,
    userCode: 'wangwu',
    userName: '王五',
    organizationId: 1,
    deptId: 20,
    deptName: '生产运营部',
    memberType: 'INTERNAL',
    jobTitle: '班组长',
    status: 1,
    tagIds: [1, 2],
    positionIds: [3]
  },
  {
    userId: 2001,
    userCode: 'zhaoliu',
    userName: '赵六',
    organizationId: 2,
    deptId: 30,
    deptName: '生产部',
    memberType: 'INTERNAL',
    jobTitle: '生产主管',
    status: 1,
    tagIds: [3],
    positionIds: [4]
  },
  {
    userId: 2002,
    userCode: 'qianqi',
    userName: '钱七',
    organizationId: 2,
    deptId: 31,
    deptName: '设备组',
    memberType: 'EXTERNAL',
    jobTitle: '设备工程师',
    status: 1,
    tagIds: [3],
    positionIds: [5]
  }
];

const ok = <T>(data: T): ApiResponse<T> => ({ code: 0, message: 'success', data });
const fail = <T>(code: number, message: string): ApiResponse<T> => ({ code, message, data: null as T });

export async function mockGetPersonPickerPage(query: PersonPickerQuery): Promise<ApiResponse<PersonPickerPage>> {
  const organizationId = Number(query['query.organizationId']);
  if (!organizationId) return fail(1001, '机构 ID 不能为空');
  const keyword = String(query['query.keyword'] ?? '').trim().toLowerCase();
  const deptId = Number(query['query.deptId'] ?? 0);
  const tagId = Number(query['query.tagId'] ?? 0);
  const positionId = Number(query['query.positionId'] ?? 0);
  const matches = people.filter((person) => {
    const searchable = `${person.userCode} ${person.userName} ${person.jobTitle ?? ''}`.toLowerCase();
    return person.organizationId === organizationId
      && person.status === 1
      && (!keyword || searchable.includes(keyword))
      && (!deptId || person.deptId === deptId)
      && (!tagId || person.tagIds.includes(tagId))
      && (!positionId || person.positionIds.includes(positionId));
  });
  const pageNum = Math.max(1, Number(query.pageNum ?? 1));
  const pageSize = Math.max(1, Number(query.pageSize ?? 20));
  return ok({
    list: matches.slice((pageNum - 1) * pageSize, pageNum * pageSize).map((person) => ({
      userId: person.userId,
      userCode: person.userCode,
      userName: person.userName,
      organizationId: person.organizationId,
      deptId: person.deptId,
      deptName: person.deptName,
      memberType: person.memberType,
      jobTitle: person.jobTitle,
      status: person.status
    })),
    total: matches.length
  });
}
