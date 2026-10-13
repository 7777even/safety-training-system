import { ref } from 'vue';
import { getPersonPickerPage } from '@/api/person-picker';
import { useUserStore } from '@/stores/modules/user';

/**
 * 用户主键 → 姓名的统一解析口径。
 *
 * 业务 VO（版本快照、成绩发布日志等）通常只回传 `xxxBy` 主键，界面需要展示姓名。
 * 这里收敛唯一实现，避免各页面各写一份：
 * 1. 只走 `/api/common/person-picker`。`/api/system/users` 需要系统管理权限，业务中心管理员会 403。
 * 2. 不按启用状态过滤。历史版本可能由已停用账号发布，纯回显不应因人员状态变化退化成编号。
 * 3. 解析失败只降级为编号，不向上抛异常，避免拖垮所在页面的详情加载。
 */

/** 单页条数取人员接口的常用上限，命中不足整页即视为没有下一页。 */
const PAGE_SIZE = 100;
/** 防御性上限，避免分页异常时长时间空转。 */
const MAX_PAGES = 5;

export function useUserNameResolver() {
  const userStore = useUserStore();
  /** 已解析的用户主键到姓名，跨多次调用累积，重复调用不会丢失既有结果。 */
  const userNames = ref<Record<number, string>>({});

  /** 当前登录用户是最可能的操作人，先登记可避免人员分页未覆盖时的名称缺失。 */
  function seedCurrentUser(): void {
    const info = userStore.userInfo;
    if (info?.userId && info.userName) userNames.value[info.userId] = info.userName;
  }

  /**
   * 批量解析用户主键为姓名。
   * 只需传入待展示的主键，已解析过的主键会被跳过；失败时静默保留编号兜底。
   * 解析范围默认取当前登录用户所属组织，跨组织回显（如只读考试详情）可显式指定。
   */
  async function loadUserNames(userIds: Array<number | null | undefined>, options?: { organizationId?: number }): Promise<void> {
    seedCurrentUser();
    const pending = new Set(userIds.filter((id): id is number => typeof id === 'number' && id > 0 && !userNames.value[id]));
    const organizationId = options?.organizationId ?? userStore.userInfo?.organizationId;
    if (!pending.size || !organizationId) return;

    const resolved: Record<number, string> = {};
    try {
      for (let page = 0; page < MAX_PAGES && pending.size > 0; page += 1) {
        const result = await getPersonPickerPage({
          pageNum: page + 1,
          pageSize: PAGE_SIZE,
          'query.organizationId': organizationId
        });
        result.list.forEach((person) => {
          if (pending.delete(person.userId)) resolved[person.userId] = person.userName;
        });
        if (result.list.length < PAGE_SIZE) break;
      }
    } catch {
      // 人员接口不可用时保留编号兜底，不阻断业务详情展示。
    }
    userNames.value = { ...userNames.value, ...resolved };
  }

  /** 传入单个主键返回姓名；无值时展示占位符，未命中时回退为用户编号。 */
  function userLabel(userId?: number | null): string {
    if (!userId) return '—';
    return userNames.value[userId] ?? `用户 #${userId}`;
  }

  return { userNames, loadUserNames, userLabel, seedCurrentUser };
}
