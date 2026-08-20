<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NIcon, NDropdown, NAvatar, NBreadcrumb, NBreadcrumbItem, NModal, NForm, NFormItem, NInput, NButton, NSpace, useMessage } from 'naive-ui'
import type { DropdownOption } from 'naive-ui'
import { useThemeStore } from '@/store/theme'
import { useAuthStore } from '@/store/auth'
import { useIsMobile } from '@/composables/useBreakpoint'
import { iconMap } from '@/utils/icons'
import { changePassword } from '@/api/user'

const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()
const authStore = useAuthStore()
const isMobile = useIsMobile()
const message = useMessage()

const emit = defineEmits<{ 'open-theme': []; 'toggle-sider': [] }>()

const breadcrumbItems = computed(() =>
  route.matched
    .filter((m) => m.meta?.title && m.path !== '/')
    .map((m) => ({ title: m.meta!.title!, path: m.path })),
)

function renderIcon(name: string) {
  const Icon = iconMap[name]
  if (!Icon) return null
  return h(NIcon, { size: 18 }, { default: () => h(Icon) })
}

const userOptions: DropdownOption[] = [
  { label: '账号安全', key: 'profile', icon: () => renderIcon('person') },
  { label: '修改密码', key: 'change-password', icon: () => renderIcon('lock') },
  { type: 'divider', key: 'd1' },
  { label: '退出登录', key: 'logout', icon: () => renderIcon('logout') },
]

function handleUserSelect(key: string) {
  if (key === 'profile') {
    router.push('/system/user-profile')
  } else if (key === 'change-password') {
    openPasswordModal()
  } else if (key === 'logout') {
    authStore.logout()
  }
}

const avatarText = computed(() => authStore.userInfo?.nickname?.charAt(0) || 'U')

// 修改密码弹窗
const showPasswordModal = ref(false)
const saving = ref(false)
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

function openPasswordModal() {
  passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  showPasswordModal.value = true
}

async function handleSubmitPassword() {
  const { oldPassword, newPassword, confirmPassword } = passwordForm.value
  if (!oldPassword || !newPassword || !confirmPassword) {
    message.warning('请填写完整')
    return
  }
  if (newPassword !== confirmPassword) {
    message.warning('两次输入的新密码不一致')
    return
  }
  if (newPassword.length < 6 || newPassword.length > 32) {
    message.warning('新密码长度需在 6-32 位之间')
    return
  }
  saving.value = true
  try {
    await changePassword({ oldPassword, newPassword })
    message.success('密码修改成功')
    showPasswordModal.value = false
  } catch {
    // 错误已由拦截器提示
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="header">
    <div class="header-left">
      <NButton text class="collapse-btn" @click="emit('toggle-sider')">
        <template #icon>
          <NIcon size="20">
            <component :is="iconMap.menu" />
          </NIcon>
        </template>
      </NButton>
      <NBreadcrumb v-if="!isMobile">
        <NBreadcrumbItem v-for="item in breadcrumbItems" :key="item.path">
          {{ item.title }}
        </NBreadcrumbItem>
      </NBreadcrumb>
    </div>

    <div class="header-right">
      <NButton text @click="themeStore.toggleDarkMode">
        <template #icon>
          <NIcon size="20">
            <component :is="themeStore.darkMode ? iconMap.sunny : iconMap.moon" />
          </NIcon>
        </template>
      </NButton>
      <NButton text @click="emit('open-theme')">
        <template #icon>
          <NIcon size="20">
            <component :is="iconMap.palette" />
          </NIcon>
        </template>
      </NButton>
      <NDropdown v-if="authStore.isLogin" :options="userOptions" @select="handleUserSelect">
        <div class="user-info">
          <NAvatar round size="small" :color="themeStore.primaryColor">
            {{ avatarText }}
          </NAvatar>
          <span v-if="!isMobile" class="username">{{ authStore.userInfo?.nickname || '用户' }}</span>
        </div>
      </NDropdown>
      <NButton v-else type="primary" size="small" @click="router.push('/login')">登录</NButton>
    </div>

    <NModal v-model:show="showPasswordModal" preset="card" title="修改密码" style="width: 420px; max-width: 90vw">
      <NForm label-placement="top">
        <NFormItem label="旧密码">
          <NInput
            v-model:value="passwordForm.oldPassword"
            type="password"
            show-password-on="click"
            placeholder="请输入当前密码"
          />
        </NFormItem>
        <NFormItem label="新密码">
          <NInput
            v-model:value="passwordForm.newPassword"
            type="password"
            show-password-on="click"
            placeholder="6-32 位"
          />
        </NFormItem>
        <NFormItem label="确认新密码">
          <NInput
            v-model:value="passwordForm.confirmPassword"
            type="password"
            show-password-on="click"
            placeholder="再次输入新密码"
            @keyup.enter="handleSubmitPassword"
          />
        </NFormItem>
        <NSpace justify="end">
          <NButton @click="showPasswordModal = false">取消</NButton>
          <NButton type="primary" :loading="saving" @click="handleSubmitPassword">确认</NButton>
        </NSpace>
      </NForm>
    </NModal>
  </div>
</template>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid var(--n-border-color, rgba(0, 0, 0, 0.06));
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.collapse-btn {
  cursor: pointer;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: var(--r-sm);
  cursor: pointer;
  transition: background 0.2s;
}

.user-info:hover {
  background: var(--c-fill-2);
}

.username {
  font-size: 14px;
}

/* 面包屑末项（当前页）：主文字色 + 加粗，呼应侧栏 active 语言 */
:deep(.n-breadcrumb-item:last-child .n-breadcrumb-item__link) {
  color: var(--c-text-1);
  font-weight: 600;
}
</style>
