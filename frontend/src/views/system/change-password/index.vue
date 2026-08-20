<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NCard, NForm, NFormItem, NInput, NButton, NSpace, NAlert, useMessage } from 'naive-ui'
import type { FormInst, FormRules } from 'naive-ui'
import { useAuthStore } from '@/store/auth'
import { changePassword } from '@/api/user'

const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const formRef = ref<FormInst | null>(null)
const saving = ref(false)
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '新密码长度需在 6-32 位之间', trigger: 'blur' },
  ],
  confirmPassword: [{ required: true, message: '请再次输入新密码', trigger: 'blur' }],
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    message.warning('两次输入的新密码不一致')
    return
  }
  saving.value = true
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    message.success('密码修改成功')
    // 清除本地强制改密标记，放行后续导航
    if (authStore.userInfo) {
      authStore.userInfo.mustChangePassword = 0
    }
    router.replace('/')
  } catch {
    // 错误已由拦截器提示
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="change-password-page">
    <NCard title="修改密码" :bordered="false" class="change-card">
      <NAlert type="info" :bordered="false" class="tip">
        当前账号使用初始默认密码，为保障安全请先设置新密码后再使用系统。
      </NAlert>
      <NForm ref="formRef" :model="form" :rules="rules" label-placement="top" class="change-form" @submit="handleSubmit">
        <NFormItem path="oldPassword" label="当前密码">
          <NInput
            v-model:value="form.oldPassword"
            type="password"
            show-password-on="click"
            placeholder="请输入当前密码"
            @keyup.enter="handleSubmit"
          />
        </NFormItem>
        <NFormItem path="newPassword" label="新密码">
          <NInput
            v-model:value="form.newPassword"
            type="password"
            show-password-on="click"
            placeholder="6-32 位"
            @keyup.enter="handleSubmit"
          />
        </NFormItem>
        <NFormItem path="confirmPassword" label="确认新密码">
          <NInput
            v-model:value="form.confirmPassword"
            type="password"
            show-password-on="click"
            placeholder="再次输入新密码"
            @keyup.enter="handleSubmit"
          />
        </NFormItem>
        <NSpace justify="end">
          <NButton type="primary" :loading="saving" attr-type="submit" @click="handleSubmit">
            确认修改
          </NButton>
        </NSpace>
      </NForm>
    </NCard>
  </div>
</template>

<style scoped>
.change-password-page {
  display: flex;
  justify-content: center;
  padding: 24px 0;
}

.change-card {
  width: 100%;
  max-width: 420px;
}

.tip {
  margin-bottom: 20px;
}
</style>