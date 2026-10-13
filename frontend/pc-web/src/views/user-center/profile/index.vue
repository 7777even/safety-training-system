<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { ArrowRight, Trophy } from '@element-plus/icons-vue';
import { getAuthorizedPortalNavigation } from '@/router/admin-dynamic-routes';
import type { MyProfile } from '@shared/types/my-profile';
import { prepareMyPassword } from '@shared/libs/my-password';
import { getMyProfile, updateMyProfile, updateMyPassword } from '@/api/my-profile';
import { MEMBER_TYPE_OPTIONS, getEnumLabel } from '@/constants/enum-options';
import { useUserStore } from '@/stores';

/** 个人资料和改密只处理当前本人，不提供用户选择和权限修改。 */
const user = useUserStore(), router = useRouter();
const profile = ref<MyProfile>(), busy = ref(false), error = ref('');
const form = reactive({ userName: '', phone: '', email: '' });
/** 中文表单提示只覆盖契约已有校验，服务端仍重新校验。 */
const profileForm = ref<FormInstance>();
const rules: FormRules = {
  userName: [{ required: true, whitespace: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }]
};
const password = reactive({ old: '', next: '', confirm: '' });
/** 只读身份按统一枚举口径转中文，未识别编码原样保留。 */
const memberTypeLabel = computed(() => profile.value ? getEnumLabel(profile.value.memberType, MEMBER_TYPE_OPTIONS) : '—');
/**
 * 「我的证书」是独立门户菜单页：未分配该菜单的账号点进来会被路由守卫送回默认页，
 * 页头入口按已授权门户导航收敛，与首页页头、个人学习中心保持同一判定口径。
 */
const canOpenCertificates = computed(() =>
  getAuthorizedPortalNavigation(user.userInfo).some((entry) => entry.path === '/portal/certificates')
);
/** 只读账号信息按固定顺序成行，缺失字段显示占位而不是留空行。 */
const facts = computed(() => profile.value ? [
  { label: '登录账号', value: profile.value.userCode },
  { label: '所属机构', value: profile.value.orgName || `机构 #${profile.value.organizationId ?? '—'}` },
  { label: '所属部门', value: profile.value.deptName || '未分配' },
  { label: '角色', value: profile.value.roles.join('、') || '未分配' },
  { label: '成员身份', value: memberTypeLabel.value }
] : []);
/** 每次加载以专用真实资料回填，禁止使用管理UserVO脱敏手机号。 */
async function load(): Promise<void> {
  profile.value = await getMyProfile();
  Object.assign(form, { userName: profile.value.userName, phone: profile.value.phone ?? '', email: profile.value.email ?? '' });
}
/** 显式构造白名单请求；版本冲突保留输入，提示重新读取。 */
async function save(): Promise<void> {
  if (!profile.value || busy.value) return;
  if (!await profileForm.value?.validate().catch(() => false)) return;
  busy.value = true; error.value = '';
  try {
    await updateMyProfile({ userName: form.userName, phone: form.phone || null, email: form.email || null, version: profile.value.version });
    await load();
    if (user.userInfo) user.userInfo.userName = profile.value?.userName ?? form.userName;
    ElMessage.success('资料已保存');
  } catch (e) { error.value = e instanceof Error ? e.message : '保存失败'; }
  finally { busy.value = false; }
}
/** 改密成功先清理所有本地账户状态，再返回登录；不记录密码。 */
async function changePassword(): Promise<void> {
  if (busy.value || !profile.value?.passwordChangeAllowed) return;
  busy.value = true; error.value = '';
  try {
    await updateMyPassword(await prepareMyPassword(password.old, password.next, password.confirm));
    password.old = password.next = password.confirm = '';
    user.clearSession(true);
    ElMessage.success('密码已修改，请重新登录');
    await router.replace('/login');
  } catch (e) { error.value = e instanceof Error ? e.message : '改密失败'; }
  finally { busy.value = false; }
}
/** 手动重新读取时同样显示接口错误。 */
async function reload(): Promise<void> { try { await load(); } catch(e) { error.value = e instanceof Error ? e.message : '资料读取失败'; } }
onMounted(reload);
</script>

<template>
  <section class="portal-list-page">
    <header class="portal-list-page__heading">
      <div>
        <p class="portal-list-page__eyebrow">MY PROFILE</p>
        <h1>本人资料</h1>
        <p>仅姓名、手机号与邮箱可自行维护；账号、机构、部门与角色由管理员管理。</p>
      </div>
      <div class="portal-heading__actions">
        <RouterLink v-if="canOpenCertificates" class="portal-entry-link" to="/portal/certificates">
          <ElIcon><Trophy /></ElIcon>
          我的证书
          <ElIcon><ArrowRight /></ElIcon>
        </RouterLink>
        <ElButton :disabled="busy" @click="reload">重新读取资料</ElButton>
      </div>
    </header>

    <ElAlert v-if="error" type="error" :title="error" :closable="false" />

    <ElCard class="portal-form-card" shadow="never">
      <div class="portal-section-head">
        <h2 class="portal-section-title">账号信息</h2>
        <span class="portal-section-hint">只读，来自当前登录账号</span>
      </div>
      <dl v-if="profile" class="profile-facts">
        <div v-for="fact in facts" :key="fact.label" class="profile-facts__row">
          <dt>{{ fact.label }}</dt>
          <dd>{{ fact.value }}</dd>
        </div>
      </dl>
    </ElCard>

    <ElCard v-if="profile" class="portal-form-card" shadow="never">
      <div class="portal-section-head">
        <h2 class="portal-section-title">可修改资料</h2>
        <span class="portal-section-hint">提交时携带当前版本，避免覆盖他人改动</span>
      </div>
      <ElForm ref="profileForm" class="portal-form" :model="form" :rules="rules" label-width="88px" @submit.prevent="save">
        <ElRow :gutter="24">
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="姓名" prop="userName"><ElInput v-model="form.userName" maxlength="100" placeholder="请输入姓名" /></ElFormItem>
          </ElCol>
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="手机号"><ElInput v-model="form.phone" maxlength="20" placeholder="请输入手机号，可留空" /></ElFormItem>
          </ElCol>
          <ElCol :span="24">
            <ElFormItem label="邮箱" prop="email"><ElInput v-model="form.email" maxlength="128" placeholder="请输入邮箱，可留空" /></ElFormItem>
          </ElCol>
        </ElRow>
        <div class="portal-form__actions">
          <ElButton type="primary" :loading="busy" @click="save">保存资料</ElButton>
        </div>
      </ElForm>
    </ElCard>

    <ElCard v-if="profile" class="portal-form-card" shadow="never">
      <div class="portal-section-head">
        <h2 class="portal-section-title">修改密码</h2>
        <span class="portal-section-hint">成功后当前账号的全部会话立即失效，需重新登录</span>
      </div>
      <p v-if="!profile.passwordChangeAllowed" class="portal-form__notice">
        {{ profile.hasLocalCredential ? '该账号受密码保护，不能本人改密。' : '该账号未绑定本地密码凭证，不能本人改密。' }}
      </p>
      <ElForm v-else class="portal-form" label-width="88px" @submit.prevent="changePassword">
        <ElRow :gutter="24">
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="旧密码"><ElInput v-model="password.old" type="password" autocomplete="current-password" placeholder="请输入旧密码" /></ElFormItem>
          </ElCol>
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="新密码"><ElInput v-model="password.next" type="password" autocomplete="new-password" placeholder="请输入新密码" /></ElFormItem>
          </ElCol>
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="确认新密码"><ElInput v-model="password.confirm" type="password" autocomplete="new-password" placeholder="请再次输入新密码" /></ElFormItem>
          </ElCol>
        </ElRow>
        <div class="portal-form__actions">
          <ElButton type="primary" :loading="busy" @click="changePassword">修改并重新登录</ElButton>
        </div>
      </ElForm>
    </ElCard>
  </section>
</template>

<style scoped lang="less">
/* 表单卡沿用学员中心白底卡片：与页头形成层级，不额外加装饰。 */
.portal-form-card {
  border-color: var(--color-border);
  border-radius: var(--radius-card);
}

.portal-form-card + .portal-form-card {
  margin-top: 0;
}

/* 只读账号字段用浅灰字段区成行，标签左、取值右对齐。 */
.profile-facts {
  margin: var(--space-4) 0 0;
  padding: 4px var(--space-4);
  border-radius: var(--radius-control);
  background: var(--color-bg-subtle);
}

.profile-facts__row {
  display: flex;
  padding: 10px 0;
  align-items: center;
  gap: var(--space-4);
}

.profile-facts__row + .profile-facts__row {
  border-top: 1px solid var(--color-bg-surface);
}

.profile-facts dt {
  flex: none;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.profile-facts dd {
  flex: 1;
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 500;
  text-align: right;
}

.portal-form {
  margin-top: var(--space-4);
}

.portal-form__actions {
  display: flex;
  justify-content: flex-end;
}

.portal-form__notice {
  margin: var(--space-4) 0 0;
  color: var(--color-text-secondary);
  line-height: 1.7;
}
</style>
