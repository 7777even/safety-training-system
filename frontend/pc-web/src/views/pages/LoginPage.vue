<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { LOGIN_ORG_CODE_STORAGE_KEY, LOGIN_ROUTE_PATH } from '@/constants/routes';
import { getDefaultMenuPath, synchronizeMenuRoutes } from '@/router/admin-dynamic-routes';
import { useUserStore } from '@/stores';
import type { SessionSlot } from '@/stores/modules/user';

/** 本地账号登录表单，只收集 Contract 要求的账号与密码。 */
const formModel = reactive({
  orgCode: '',
  userCode: '',
  password: ''
});

const formRules: FormRules = {
  orgCode: [{required: true, message: '请输入企业编码', trigger: 'blur'}],
  userCode: [{ required: true, message: '请输入账号或用户编码。', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码。', trigger: 'blur' }]
};

const formRef = ref<FormInstance>();
const isSubmitting = ref(false);
const errorMessage = ref('');
const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const availableSessionSlots = ref<SessionSlot[]>([]);
/** 专属链接只提供公开的机构编码，仍由现有登录接口校验账号和密码。 */
const linkedOrgCode = computed(() => {
  const code = route.query.orgCode;
  return typeof code === 'string' && /^[A-Z0-9_-]{2,64}$/i.test(code) ? code.toUpperCase() : undefined;
});
watch(linkedOrgCode, (code) => { formModel.orgCode = code ?? ''; }, { immediate: true });
// const restoringSlotId = ref<string>();

/** 读取未过期的已保存账户，供浏览器重启后恢复。 */
function loadSessionSlots(): void {
  availableSessionSlots.value = userStore.listSessionSlots();
}

/** 恢复选中的持久化账户，并重新加载该账户的菜单权限。 */
// async function restoreSavedSession(slot: SessionSlot): Promise<void> {
//   restoringSlotId.value = slot.slotId;
//   errorMessage.value = '';

//   try {
//     await userStore.restoreSessionSlot(slot.slotId);
//     const menuPaths = synchronizeMenuRoutes(router, userStore.userInfo);
//     await router.replace(getDefaultMenuPath(userStore.userInfo) ?? menuPaths[0] ?? LOGIN_ROUTE_PATH);
//   } catch (error) {
//     errorMessage.value = error instanceof Error ? error.message : '保存的登录会话已失效，请重新登录';
//     loadSessionSlots();
//   } finally {
//     restoringSlotId.value = undefined;
//   }
// }

// /** 删除一个不再使用的已保存账户槽位。 */
// function removeSavedSession(slotId: string): void {
//   userStore.removeSessionSlot(slotId);
//   loadSessionSlots();
// }

onMounted(loadSessionSlots);

/** 已登录用户打开另一机构链接时，先显式退出当前账号。 */
async function switchAccount(): Promise<void> {
  try {
    await userStore.logout();
  } catch {
    // Store 已清除本地会话，仍允许使用当前机构链接重新登录。
  } finally {
    synchronizeMenuRoutes(router, null);
  }
}

/** 放弃专属入口，恢复可手输机构编码的通用登录页。 */
async function switchOrganization(): Promise<void> {
  await router.replace({ path: LOGIN_ROUTE_PATH, query: route.query.redirect ? { redirect: route.query.redirect } : undefined });
}

/** 提交登录并在成功后回到原目标页或门户首页。 */
async function submitLogin(): Promise<void> {
  const isValid = await formRef.value?.validate().catch(() => false);

  if (!isValid) {
    return;
  }

  errorMessage.value = '';
  isSubmitting.value = true;

  try {
    await userStore.loginWithPassword(formModel.orgCode.trim(), formModel.userCode.trim(), formModel.password);
    if (userStore.userInfo?.userCode === 'superAdmin') {
      globalThis.sessionStorage.removeItem(LOGIN_ORG_CODE_STORAGE_KEY);
    } else {
      globalThis.sessionStorage.setItem(LOGIN_ORG_CODE_STORAGE_KEY, formModel.orgCode.trim().toUpperCase());
    }
    synchronizeMenuRoutes(router, userStore.userInfo);
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : undefined;
    const defaultPath = getDefaultMenuPath(userStore.userInfo) ?? LOGIN_ROUTE_PATH;
    await router.replace(
      redirect && router.resolve(redirect).matched.some((record) => typeof record.meta.menuId === 'number' || record.meta.m4PublicFlow === true)
        ? redirect
        : defaultPath
    );
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败，请稍后重试。';
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <main class="login-page">
    <section class="brand-panel" aria-label="安全教育培训系统介绍">
      <div class="brand-content">
        <div class="brand-mark" aria-hidden="true">安</div>
        <p class="brand-eyebrow">SAFETY TRAINING SYSTEM</p>
        <h1>安全教育培训系统</h1>
        <p class="brand-description">让安全知识学习、培训任务与组织管理更加清晰可靠。</p>
        <div class="brand-illustration" aria-hidden="true">
          <span class="shield">✓</span>
          <span class="line line-one" />
          <span class="line line-two" />
          <span class="line line-three" />
        </div>
      </div>
    </section>

    <section class="form-panel">
      <div class="login-card">
        <p class="welcome">欢迎回来</p>
        <h2>登录系统</h2>
        <p class="card-description">请输入账号与密码继续使用系统。</p>

        <!-- <section v-if="availableSessionSlots.length > 0" class="saved-sessions" aria-label="已保存的登录账户">
          <div class="saved-sessions-heading">
            <span>恢复已保存账户</span>
            <small>选择后无需重新输入密码</small>
          </div>
          <div v-for="slot in availableSessionSlots" :key="slot.slotId" class="saved-session-item">
            <button
              class="saved-session-button"
              type="button"
              :disabled="Boolean(restoringSlotId)"
              @click="restoreSavedSession(slot)"
            >
              <strong>{{ slot.userInfo.userName || slot.userInfo.userCode || '已保存账户' }}</strong>
              <span>{{ slot.userInfo.userCode || '账户信息' }}</span>
            </button>
            <ElButton link type="danger" :disabled="Boolean(restoringSlotId)" @click="removeSavedSession(slot.slotId)">
              移除
            </ElButton>
          </div>
        </section> -->

        <div v-if="userStore.token" class="login-entry-notice">
          <p>当前已有账号登录。若要使用此机构入口，请先退出当前账号。</p>
          <ElButton type="primary" @click="switchAccount">退出并切换账号</ElButton>
        </div>
        <ElForm v-else ref="formRef" :model="formModel" :rules="formRules" label-position="top" @submit.prevent="submitLogin">
          <!-- 机构信息与次级切换操作分列展示，保持与移动端一致的视觉层级。 -->
          <div v-if="linkedOrgCode" class="organization-entry">
            <div class="organization-info">
              <span class="organization-label">当前机构</span>
              <strong class="organization-code">{{ linkedOrgCode }}</strong>
            </div>
            <ElButton class="organization-switch" link type="primary" @click="switchOrganization">切换机构</ElButton>
          </div>
          <ElFormItem v-else label="企业编码" prop="orgCode">
            <ElInput v-model="formModel.orgCode" placeholder="请输入企业编码" autocomplete="organization" />
          </ElFormItem>
          <p v-if="route.query.orgCode && !linkedOrgCode" role="alert">登录链接中的机构编码无效，请手动输入。</p>
          <ElFormItem label="账号" prop="userCode">
            <ElInput v-model="formModel.userCode" placeholder="请输入账号或用户编码" autocomplete="username" />
          </ElFormItem>
          <ElFormItem label="密码" prop="password">
            <ElInput
              v-model="formModel.password"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
              show-password
              @keyup.enter="submitLogin"
            />
          </ElFormItem>
          <p v-if="errorMessage" class="error-message" role="alert">
            {{ errorMessage }}
          </p>
          <ElButton class="submit-button" type="primary" native-type="submit" :loading="isSubmitting">登录</ElButton>
        </ElForm>
      </div>
    </section>
  </main>
</template>

<style scoped lang="less">
.login-page {
  display: grid;
  min-height: 100vh;
  grid-template-columns: minmax(520px, 58fr) minmax(420px, 42fr);
  background: var(--color-bg-surface);
}

.brand-panel {
  display: flex;
  align-items: center;
  padding: 64px clamp(48px, 8vw, 144px);
  color: #ffffff;
  background: var(--color-primary-hover);
}

.brand-content {
  max-width: 520px;
}
.brand-mark {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 12px;
  background: #ffffff;
  color: var(--color-primary);
  font-size: 22px;
  font-weight: 700;
}
.brand-eyebrow {
  margin: 48px 0 12px;
  color: #b9d5ff;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1.8px;
}
h1 {
  margin: 0;
  font-size: clamp(36px, 3.2vw, 52px);
  line-height: 1.2;
}
.brand-description {
  max-width: 400px;
  margin: 20px 0 0;
  color: #dceaff;
  font-size: 17px;
  line-height: 1.8;
  white-space: nowrap;
}
.brand-illustration {
  position: relative;
  width: 340px;
  height: 188px;
  margin-top: 72px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.08);
}
.shield {
  position: absolute;
  top: -28px;
  right: 32px;
  display: grid;
  width: 80px;
  height: 80px;
  place-items: center;
  border-radius: 50% 50% 44% 44%;
  background: #56a1ff;
  box-shadow: 0 12px 24px rgba(4, 31, 70, 0.3);
  color: #ffffff;
  font-size: 36px;
  font-weight: 700;
}
.line {
  position: absolute;
  left: 36px;
  height: 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.8);
}
.line-one {
  top: 52px;
  width: 148px;
}
.line-two {
  top: 88px;
  width: 220px;
  background: rgba(185, 213, 255, 0.75);
}
.line-three {
  top: 124px;
  width: 174px;
  background: rgba(185, 213, 255, 0.5);
}

.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: var(--color-bg-page);
}
.login-card {
  width: min(100%, 400px);
  padding: 40px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-card);
}
.welcome {
  margin: 0;
  color: var(--color-primary);
  font-size: 14px;
  font-weight: 600;
}
.login-card h2 {
  margin: 10px 0 8px;
  color: var(--color-text-primary);
  font-size: 30px;
}
.card-description {
  margin: 0 0 28px;
  color: var(--color-text-secondary);
  font-size: 14px;
}
.organization-entry {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  padding: 14px 16px;
  border-radius: var(--radius-control);
  background: var(--color-primary-subtle);
}
.organization-info {
  flex: 1;
  min-width: 0;
}
.organization-label {
  display: block;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.organization-code {
  display: block;
  margin-top: 4px;
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.organization-switch {
  flex-shrink: 0;
  min-height: 40px;
}
.saved-sessions {
  margin: 0 0 24px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-subtle);
}
.saved-sessions-heading,
.saved-session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.saved-sessions-heading {
  margin-bottom: 8px;
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 600;
}
.saved-sessions-heading small,
.saved-session-button span {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 400;
}
.saved-session-item {
  gap: 8px;
  padding: 8px 0;
  border-top: 1px solid var(--color-border);
}
.saved-session-button {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-text-primary);
  cursor: pointer;
  text-align: left;
}
.saved-session-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.error-message {
  margin: -4px 0 16px;
  color: var(--color-danger);
  font-size: 13px;
}
.submit-button {
  width: 100%;
  height: 40px;
  font-weight: 600;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }
  .brand-panel {
    min-height: 240px;
    padding: 40px;
  }
  .brand-eyebrow {
    margin-top: 28px;
  }
  .brand-illustration {
    display: none;
  }
  .form-panel {
    align-items: flex-start;
    padding: 32px 20px;
  }
  .login-card {
    padding: 28px;
  }
}
</style>
