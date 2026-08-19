<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NCard, NForm, NFormItem, NInput, NButton, NIcon, useMessage } from 'naive-ui'
import type { FormInst, FormRules } from 'naive-ui'
import { useAuthStore } from '@/store/auth'
import { iconMap } from '@/utils/icons'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const authStore = useAuthStore()

const formRef = ref<FormInst | null>(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin(e: Event) {
  e.preventDefault()
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    await authStore.login({ username: form.username, password: form.password })
    message.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch (err) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <div class="login-bg" />
    <NCard class="login-card" :bordered="false" size="large">
      <div class="login-header">
        <NIcon size="40" color="var(--c-brand)">
          <component :is="iconMap.grid" />
        </NIcon>
        <h1 class="login-title">Stellar</h1>
        <p class="login-subtitle">个人知识沉淀池 · 工具与实验集</p>
      </div>
      <NForm ref="formRef" :model="form" :rules="rules" size="large" @submit="handleLogin">
        <NFormItem path="username">
          <NInput
            v-model:value="form.username"
            placeholder="请输入用户名"
            clearable
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <NIcon><component :is="iconMap.person" /></NIcon>
            </template>
          </NInput>
        </NFormItem>
        <NFormItem path="password">
          <NInput
            v-model:value="form.password"
            type="password"
            show-password-on="click"
            placeholder="请输入密码"
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <NIcon><component :is="iconMap.settings" /></NIcon>
            </template>
          </NInput>
        </NFormItem>
        <NButton
          type="primary"
          block
          size="large"
          :loading="loading"
          attr-type="submit"
          @click="handleLogin"
        >
          登 录
        </NButton>
      </NForm>
    </NCard>
  </div>
</template>

<style scoped>
.login-container {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 100vh;
  min-height: 100dvh;
  overflow: hidden;
  padding: 16px;
}

.login-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, var(--c-brand) 0%, var(--c-info) 100%);
}

.login-bg::before,
.login-bg::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
}

.login-bg::before {
  width: 480px;
  height: 480px;
  top: -160px;
  right: -120px;
}

.login-bg::after {
  width: 360px;
  height: 360px;
  bottom: -120px;
  left: -100px;
}

.login-card {
  position: relative;
  z-index: 1;
  width: 380px;
  max-width: 90vw;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
}

.login-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;
}

.login-title {
  margin: 12px 0 4px;
  font-size: 24px;
  font-weight: 700;
}

.login-subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--c-text-3);
}
</style>
