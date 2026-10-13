// TODO replace real api：机构协作补充接口联调完成后，按接口逐项移除对应 Mock 分支。
/* eslint-disable @typescript-eslint/no-explicit-any -- Contract 查询参数使用动态 query.* 键，待真实接口接入时随分支移除。 */

/** 与运行期 Result 外壳一致的 Contract Mock 响应。 */
export interface OrgCollaborationMockResult<TData> {
  code: number;
  message: string;
  data: TData;
}

interface TagRecord {
  tagId: number;
  organizationId: number;
  tagName: string;
  description?: string;
  status: 0 | 1;
  version: number;
}
interface PositionRecord {
  positionId: number;
  organizationId: number;
  positionCode: string;
  positionName: string;
  description?: string;
  status: 0 | 1;
  version: number;
}
interface MemberRecord {
  orgMemberId: number;
  organizationId: number;
  userId: number;
  memberType: string;
  employeeNo?: string;
  email?: string;
  jobTitle?: string;
  entryDate?: string;
  status: 0 | 1;
  version: number;
  tagIds: number[];
  positionIds: number[];
  primaryPositionId?: number;
}
interface AudienceRecord {
  audienceId: number;
  organizationId: number;
  audienceName: string;
  audienceKind: string;
  businessType?: string | null;
  businessId?: number | null;
  purpose: string;
  resolveMode: string;
  isInternalLinkAllowed: 0 | 1;
  isExternalLinkAllowed: 0 | 1;
  status: 0 | 1;
  version: number;
  createTime: string;
  updateTime?: string | null;
  rules: Array<{ decision: 'INCLUDE' | 'EXCLUDE'; subjectType: 'ALL' | 'DEPT' | 'TAG' | 'POSITION' | 'USER' | 'AUDIENCE'; subjectId: number }>;
  frozenUserIds: number[];
}
interface GrantRecord {
  orgGrantId: number;
  ownerOrganizationId: number;
  resourceType: string;
  resourceId: number;
  granteeOrganizationId: number;
  grantLevel: string;
  validFrom?: string;
  validTo?: string;
  status: 0 | 1;
  version: number;
}

/** 当前 Mock 数据仅服务页面交互，不写入浏览器或后端。 */
const store: {
  tags: TagRecord[];
  positions: PositionRecord[];
  members: MemberRecord[];
  audiences: AudienceRecord[];
  grants: GrantRecord[];
} = {
  tags: [],
  positions: [],
  members: [],
  audiences: [],
  grants: []
};
const counters = { tag: 1, position: 1, member: 1, audience: 1, grant: 1 };
const ok = <TData>(data: TData): OrgCollaborationMockResult<TData> => ({ code: 0, message: '操作成功', data });
const failed = <TData>(code: number, message: string, data: TData): OrgCollaborationMockResult<TData> => ({ code, message, data });
const page = <TItem>(list: TItem[], pageNum: number, pageSize: number) => ({
  list: list.slice((pageNum - 1) * pageSize, pageNum * pageSize),
  total: list.length
});

/** 重置所有机构协作 Mock 数据，供独立测试或重新进入演示流程使用。 */
export function resetOrgCollaborationMock(): void {
  store.tags = [];
  store.positions = [];
  store.members = [];
  store.audiences = [];
  store.grants = [];
  counters.tag = 1;
  counters.position = 1;
  counters.member = 1;
  counters.audience = 1;
  counters.grant = 1;
}

export const mockGetOrgTagPage = async (params: Record<string, any>) =>
  ok(
    page(
      store.tags.filter(
        (item) =>
          (!params['query.organizationId'] || item.organizationId === params['query.organizationId']) &&
          (!params['query.tagName'] || item.tagName.includes(params['query.tagName'])) &&
          (params['query.status'] === undefined || item.status === params['query.status'])
      ),
      params.pageNum,
      params.pageSize
    )
  );
export const mockCreateOrgTag = async (data: Omit<TagRecord, 'tagId' | 'version'>) => {
  const tagId = counters.tag++;
  store.tags.push({ ...data, tagId, version: 0 });
  return ok(tagId);
};
export const mockGetOrgTagDetail = async (tagId: number) => {
  const item = store.tags.find((value) => value.tagId === tagId);
  return item ? ok(item) : failed(5011, '机构标签不存在', null);
};
export const mockUpdateOrgTag = async (tagId: number, data: Omit<TagRecord, 'tagId' | 'organizationId'>) => {
  const item = store.tags.find((value) => value.tagId === tagId);
  if (!item) return failed(5011, '机构标签不存在', null);
  if (item.version !== data.version) return failed(2040, '数据已被更新，请刷新后重试', null);
  Object.assign(item, data, { version: item.version + 1 });
  return ok(null);
};
export const mockDeleteOrgTag = async (tagId: number) => {
  const index = store.tags.findIndex((value) => value.tagId === tagId);
  if (index < 0) return failed(5011, '机构标签不存在', null);
  if (store.members.some((item) => item.tagIds.includes(tagId))) return failed(5006, '标签已被成员使用，不能删除', null);
  store.tags.splice(index, 1);
  return ok(null);
};

export const mockGetOrgPositionPage = async (params: Record<string, any>) =>
  ok(
    page(
      store.positions.filter(
        (item) =>
          (!params['query.organizationId'] || item.organizationId === params['query.organizationId']) &&
          (!params['query.positionCode'] || item.positionCode.includes(params['query.positionCode'])) &&
          (!params['query.positionName'] || item.positionName.includes(params['query.positionName'])) &&
          (params['query.status'] === undefined || item.status === params['query.status'])
      ),
      params.pageNum,
      params.pageSize
    )
  );
export const mockCreateOrgPosition = async (data: Omit<PositionRecord, 'positionId' | 'version'>) => {
  if (store.positions.some((item) => item.organizationId === data.organizationId && item.positionCode === data.positionCode))
    return failed(5005, '岗位编码已存在', 0);
  const positionId = counters.position++;
  store.positions.push({ ...data, positionId, version: 0 });
  return ok(positionId);
};
export const mockGetOrgPositionDetail = async (positionId: number) => {
  const item = store.positions.find((value) => value.positionId === positionId);
  return item ? ok(item) : failed(5011, '机构岗位不存在', null);
};
export const mockUpdateOrgPosition = async (positionId: number, data: Omit<PositionRecord, 'positionId' | 'organizationId'>) => {
  const item = store.positions.find((value) => value.positionId === positionId);
  if (!item) return failed(5011, '机构岗位不存在', null);
  if (item.version !== data.version) return failed(2040, '数据已被更新，请刷新后重试', null);
  Object.assign(item, data, { version: item.version + 1 });
  return ok(null);
};
export const mockDeleteOrgPosition = async (positionId: number) => {
  const index = store.positions.findIndex((value) => value.positionId === positionId);
  if (index < 0) return failed(5011, '机构岗位不存在', null);
  if (store.members.some((item) => item.positionIds.includes(positionId))) return failed(5006, '岗位已被成员使用，不能删除', null);
  store.positions.splice(index, 1);
  return ok(null);
};

export const mockGetAudiencePage = async (params: Record<string, any>) =>
  ok(
    page(
      store.audiences.filter(
        (item) =>
          (!params['query.organizationId'] || item.organizationId === params['query.organizationId']) &&
          (!params['query.audienceName'] || item.audienceName.includes(params['query.audienceName'])) &&
          (!params['query.audienceKind'] || item.audienceKind === params['query.audienceKind']) &&
          (!params['query.businessType'] || item.businessType === params['query.businessType']) &&
          (!params['query.businessId'] || item.businessId === params['query.businessId']) &&
          (!params['query.purpose'] || item.purpose === params['query.purpose']) &&
          (!params['query.resolveMode'] || item.resolveMode === params['query.resolveMode']) &&
          (params['query.status'] === undefined || item.status === params['query.status'])
      ),
      params.pageNum,
      params.pageSize
    )
  );
export const mockGetAudienceDetail = async (audienceId: number) => {
  const item = store.audiences.find((value) => value.audienceId === audienceId);
  return item ? ok({ ...item, rules: item.rules.map((rule, index) => ({ ...rule, audienceRuleId: index + 1 })) }) : failed(4101, '参与范围不存在', null);
};
export const mockCreateAudience = async (data: Omit<AudienceRecord, 'audienceId' | 'version' | 'rules' | 'frozenUserIds' | 'createTime' | 'updateTime' | 'isInternalLinkAllowed' | 'isExternalLinkAllowed'> & { isInternalLinkAllowed?: 0 | 1; isExternalLinkAllowed?: 0 | 1; rules?: AudienceRecord['rules'] }) => {
  if (data.audienceKind === 'BOUND' && (!data.businessType || !data.businessId)) return failed(4104, '绑定范围必须指定业务对象', 0);
  if (data.audienceKind === 'BOUND' && store.audiences.some((item) => item.organizationId === data.organizationId && item.businessType === data.businessType && item.businessId === data.businessId && item.purpose === data.purpose)) return failed(4102, '同业务同用途的参与范围已存在', 0);
  const audienceId = counters.audience++;
  store.audiences.push({ ...data, audienceId, version: 0, rules: [], frozenUserIds: [], createTime: new Date().toISOString(), isInternalLinkAllowed: data.isInternalLinkAllowed ?? 0, isExternalLinkAllowed: data.isExternalLinkAllowed ?? 0 });
  return ok(audienceId);
};
export const mockUpdateAudience = async (audienceId: number, data: Pick<AudienceRecord, 'version' | 'audienceName' | 'status'> & Partial<Pick<AudienceRecord, 'isInternalLinkAllowed' | 'isExternalLinkAllowed'>>) => {
  const item = store.audiences.find((value) => value.audienceId === audienceId);
  if (!item) return failed(4101, '参与范围不存在', null);
  if (item.version !== data.version) return failed(2040, '数据已被更新，请刷新后重试', null);
  Object.assign(item, data, { version: item.version + 1, updateTime: new Date().toISOString() });
  return ok(null);
};
export const mockDeleteAudience = async (audienceId: number) => {
  const index = store.audiences.findIndex((value) => value.audienceId === audienceId);
  if (index < 0) return failed(5011, '参与范围不存在', null);
  store.audiences.splice(index, 1);
  return ok(null);
};
export const mockReplaceAudienceRules = async (audienceId: number, rules: AudienceRecord['rules']) => {
  const item = store.audiences.find((value) => value.audienceId === audienceId);
  if (!item) return failed(4101, '参与范围不存在', null);
  const seen = new Set<string>();
  for (const rule of rules) {
    if ((rule.subjectType === 'ALL' && rule.subjectId !== 0) || (rule.subjectType !== 'ALL' && rule.subjectId <= 0)) return failed(4106, '规则主体无效', null);
    const key = `${rule.decision}/${rule.subjectType}/${rule.subjectId}`;
    if (seen.has(key)) return failed(4106, '规则重复', null);
    seen.add(key);
  }
  item.rules = rules;
  item.version += 1;
  item.updateTime = new Date().toISOString();
  return ok(null);
};
function resolveAudienceUserIds(audienceId: number, trail: number[] = []): number[] {
  const item = store.audiences.find((value) => value.audienceId === audienceId);
  if (!item || trail.includes(audienceId) || trail.length >= 3) return [];
  const members = store.members.filter((member) => member.organizationId === item.organizationId && member.status === 1);
  const matches = (rule: AudienceRecord['rules'][number], member: MemberRecord): boolean => {
    if (rule.subjectType === 'ALL') return true;
    if (rule.subjectType === 'TAG') return member.tagIds.includes(rule.subjectId);
    if (rule.subjectType === 'POSITION') return member.positionIds.includes(rule.subjectId);
    if (rule.subjectType === 'USER') return member.userId === rule.subjectId;
    if (rule.subjectType === 'DEPT') return false;
    return resolveAudienceUserIds(rule.subjectId, [...trail, audienceId]).includes(member.userId);
  };
  const result = new Set(members.filter((member) => item.rules.some((rule) => rule.decision === 'INCLUDE' && matches(rule, member))).map((member) => member.userId));
  for (const member of members) if (item.rules.some((rule) => rule.decision === 'EXCLUDE' && matches(rule, member))) result.delete(member.userId);
  return [...result];
}
export const mockPreviewAudience = async (audienceId: number) => {
  const item = store.audiences.find((value) => value.audienceId === audienceId);
  if (!item) return failed(4101, '参与范围不存在', { audienceId, resolveMode: 'DYNAMIC', matchedCount: 0, userIds: [] });
  const userIds = resolveAudienceUserIds(audienceId);
  return ok({ audienceId, resolveMode: item.resolveMode, matchedCount: userIds.length, userIds });
};
export const mockFreezeAudience = async (audienceId: number) => {
  const item = store.audiences.find((value) => value.audienceId === audienceId);
  if (!item) return failed(4101, '参与范围不存在', { memberCount: 0 });
  if (item.resolveMode !== 'FROZEN') return failed(4105, '仅冻结模式可冻结名单', { memberCount: 0 });
  const preview = await mockPreviewAudience(audienceId);
  if (preview.code !== 0) return failed(preview.code, preview.message, { memberCount: 0 });
  item.frozenUserIds = preview.data.userIds;
  return ok({ memberCount: item.frozenUserIds.length });
};
export const mockGetAudienceMembers = async (audienceId: number, params: Record<string, any>) => {
  const item = store.audiences.find((value) => value.audienceId === audienceId);
  if (!item) return failed(4101, '参与范围不存在', { list: [], total: 0 });
  if (item.resolveMode !== 'FROZEN') return failed(4105, '仅冻结模式可查询成员', { list: [], total: 0 });
  const members = item.frozenUserIds.map((userId, index) => ({ audienceMemberId: index + 1, audienceId, userId, userCode: String(userId), userName: `用户${userId}`, finalDecision: 'INCLUDE' as const, resolvedTime: item.updateTime || item.createTime }));
  return ok(page(members, params.pageNum || 1, params.pageSize || 20));
};
export const mockContainsAudienceUser = async (audienceId: number, userId: number) => {
  const item = store.audiences.find((value) => value.audienceId === audienceId);
  if (!item) return failed(4101, '参与范围不存在', { audienceId, userId, contained: false });
  const userIds = item.resolveMode === 'FROZEN' ? item.frozenUserIds : resolveAudienceUserIds(audienceId);
  return ok({ audienceId, userId, contained: userIds.includes(userId) });
};

export const mockGetResourceGrantPage = async (params: Record<string, any>) => ok(page(store.grants, params.pageNum, params.pageSize));
export const mockCreateResourceGrant = async (data: Omit<GrantRecord, 'orgGrantId' | 'version'>) => {
  const orgGrantId = counters.grant++;
  store.grants.push({ ...data, orgGrantId, version: 0 });
  return ok(orgGrantId);
};
export const mockUpdateResourceGrant = async (
  id: number,
  data: Omit<GrantRecord, 'orgGrantId' | 'ownerOrganizationId' | 'resourceType' | 'resourceId' | 'granteeOrganizationId'>
) => {
  const item = store.grants.find((value) => value.orgGrantId === id);
  if (!item) return failed(5011, '资源授权不存在', null);
  if (item.version !== data.version) return failed(2040, '数据已被更新，请刷新后重试', null);
  Object.assign(item, data, { version: item.version + 1 });
  return ok(null);
};
export const mockDeleteResourceGrant = async (id: number) => {
  const index = store.grants.findIndex((value) => value.orgGrantId === id);
  if (index < 0) return failed(5011, '资源授权不存在', null);
  store.grants.splice(index, 1);
  return ok(null);
};
