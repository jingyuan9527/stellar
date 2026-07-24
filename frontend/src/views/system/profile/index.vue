<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NCard, NFormItem, NInput, NButton, NUpload, NImage, NSpace, useMessage } from 'naive-ui'
import type { UploadCustomRequestOptions } from 'naive-ui'
import { getProfile, updateProfile } from '@/api/profile'
import { uploadFile } from '@/api/file'
import type { Profile } from '@/types/api'

const message = useMessage()
const saving = ref(false)
const form = ref<Partial<Profile>>({})

async function load() {
  try {
    form.value = await getProfile()
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleSave() {
  saving.value = true
  try {
    await updateProfile(form.value)
    message.success('已保存')
  } catch {
    // 错误已由拦截器提示
  } finally {
    saving.value = false
  }
}

async function customUpload({ file, onFinish, onError }: UploadCustomRequestOptions) {
  const f = file.file as File
  if (!f) {
    onError()
    return
  }
  try {
    form.value.avatar = await uploadFile(f)
    message.success('头像上传成功')
    onFinish()
  } catch {
    onError()
  }
}

onMounted(load)
</script>

<template>
  <div class="profile-admin-page">
    <NCard title="个人资料" :bordered="false">
      <NSpace vertical :size="16" style="max-width: 560px">
        <NFormItem label="昵称">
          <NInput v-model:value="form.nickname" placeholder="展示昵称" />
        </NFormItem>
        <NFormItem label="头像">
          <NSpace align="center" :size="12">
            <NUpload :custom-request="customUpload" :show-file-list="false" accept="image/*">
              <NButton>上传头像</NButton>
            </NUpload>
            <NImage
              v-if="form.avatar"
              :src="form.avatar"
              width="80"
              height="80"
              object-fit="cover"
              round
            />
          </NSpace>
        </NFormItem>
        <NFormItem label="简介">
          <NInput v-model:value="form.bio" type="textarea" :autosize="{ minRows: 2 }" placeholder="一句话介绍自己" />
        </NFormItem>
        <NFormItem label="技能标签">
          <NInput v-model:value="form.skills" placeholder="逗号分隔，如 Java,Vue,运维" />
        </NFormItem>
        <NFormItem label="外链 JSON">
          <NInput
            v-model:value="form.links"
            type="textarea"
            :autosize="{ minRows: 3 }"
            placeholder='如 {"github":"https://github.com/xxx","email":"x@x.com"}'
          />
        </NFormItem>
        <NButton type="primary" :loading="saving" @click="handleSave">保存</NButton>
      </NSpace>
    </NCard>
  </div>
</template>

<style scoped>
.profile-admin-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
