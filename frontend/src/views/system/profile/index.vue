<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NCard, NFormItem, NInput, NButton, NUpload, NImage, NSpace, useMessage } from 'naive-ui'
import type { UploadCustomRequestOptions } from 'naive-ui'
import { getProfile, updateProfile } from '@/api/profile'
import { uploadFile } from '@/api/file'
import type { Profile } from '@/types/api'

const message = useMessage()
const saving = ref(false)
const form = ref<Partial<Profile>>({})

const aboutPreview = computed(() => Boolean(form.value.about))

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
      <NSpace vertical :size="16" style="max-width: 640px">
        <NFormItem label="昵称">
          <NInput v-model:value="form.nickname" placeholder="展示昵称" />
        </NFormItem>
        <NFormItem label="头衔">
          <NInput v-model:value="form.title" placeholder="如：全栈开发 / 运维" />
        </NFormItem>
        <NFormItem label="所在地">
          <NInput v-model:value="form.location" placeholder="如：杭州" />
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
        <NFormItem label="一句话简介">
          <NInput v-model:value="form.bio" type="textarea" :autosize="{ minRows: 2 }" placeholder="首页 / about 的 hero 简介一句话" />
        </NFormItem>
        <NFormItem label="关于我（富文本 HTML）">
          <div class="about-editor">
            <NInput
              v-model:value="form.about"
              type="textarea"
              :autosize="{ minRows: 6, maxRows: 20 }"
              placeholder="支持 HTML 标签，如 &lt;p&gt;段落&lt;/p&gt;、&lt;h3&gt;小标题&lt;/h3&gt;、&lt;ul&gt;&lt;li&gt;列表&lt;/li&gt;&lt;/ul&gt;；前端 v-html 渲染"
            />
            <div v-if="aboutPreview" class="about-preview">
              <div class="about-preview-label">预览</div>
              <div class="about-preview-body" v-html="form.about"></div>
            </div>
          </div>
        </NFormItem>
        <NFormItem label="技能标签">
          <NInput v-model:value="form.skills" placeholder="逗号分隔，如 Java,Vue,运维" />
        </NFormItem>
        <NFormItem label="外链 JSON">
          <NInput
            v-model:value="form.links"
            type="textarea"
            :autosize="{ minRows: 3 }"
            placeholder='如 {"github":"https://github.com/xxx","email":"x@x.com","site":"https://xxx.com"}'
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

.about-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.about-preview {
  border: 1px solid rgba(127, 127, 127, 0.2);
  border-radius: 6px;
  padding: 12px 16px;
}

.about-preview-label {
  font-size: 12px;
  opacity: 0.5;
  margin-bottom: 8px;
}

.about-preview-body {
  line-height: 1.8;
  font-size: 14px;
}

.about-preview-body :deep(p) {
  margin: 0 0 12px;
}

.about-preview-body :deep(h3) {
  margin: 12px 0 8px;
}

.about-preview-body :deep(ul),
.about-preview-body :deep(ol) {
  margin: 0 0 12px;
  padding-left: 24px;
}
</style>
