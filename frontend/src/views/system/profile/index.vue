<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NCard, NFormItem, NInput, NButton, NUpload, NImage, NSpace,
  NDataTable, NModal, NPopconfirm, NIcon, useMessage,
} from 'naive-ui'
import type { DataTableColumns, UploadCustomRequestOptions } from 'naive-ui'
import { getProfile, updateProfile, getProfileProjects, createProfileProject, updateProfileProject, deleteProfileProject } from '@/api/profile'
import { uploadFile } from '@/api/file'
import type { Profile, ProfileProject } from '@/types/api'
import { iconMap } from '@/utils/icons'

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

// ============== 项目展示管理 ==============
const projects = ref<ProfileProject[]>([])
const projectLoading = ref(false)

async function loadProjects() {
  projectLoading.value = true
  try {
    projects.value = await getProfileProjects()
  } catch {
    // 错误已由拦截器提示
  } finally {
    projectLoading.value = false
  }
}

const projectColumns: DataTableColumns<ProfileProject> = [
  { title: '项目名', key: 'name', width: 160, ellipsis: { tooltip: true } },
  {
    title: '线上地址', key: 'siteUrl', width: 220, ellipsis: { tooltip: true },
    render: (row) => row.siteUrl
      ? h(NButton, { text: true, tag: 'a', href: row.siteUrl, target: '_blank', type: 'primary' },
          { default: () => row.siteUrl })
      : h('span', { style: 'color:var(--c-text-3)' }, '-'),
  },
  {
    title: '源码地址', key: 'sourceUrl', width: 220, ellipsis: { tooltip: true },
    render: (row) => row.sourceUrl
      ? h(NButton, { text: true, tag: 'a', href: row.sourceUrl, target: '_blank', type: 'primary' },
          { default: () => row.sourceUrl })
      : h('span', { style: 'color:var(--c-text-3)' }, '-'),
  },
  { title: '简介', key: 'description', ellipsis: { tooltip: true }, render: (row) => row.description || '-' },
  {
    title: '操作', key: 'actions', width: 120, fixed: 'right',
    render: (row) => h(NSpace, { size: 0 },
      {
        default: () => [
          h(NButton, { size: 'small', text: true, onClick: () => openEditProject(row) },
            { default: () => '编辑' }),
          h(NPopconfirm, { onPositiveClick: () => handleDeleteProject(row.id) },
            {
              trigger: () => h(NButton, { size: 'small', text: true, type: 'error' },
                { icon: () => h(NIcon, null, { default: () => h(iconMap.trash) }), default: () => '删除' }),
              default: () => `确认删除「${row.name}」？`,
            }),
        ],
      }),
  },
]

const showProjectModal = ref(false)
const projectSaving = ref(false)
const projectForm = reactive<{ id: number | null; name: string; siteUrl: string; sourceUrl: string; description: string }>({
  id: null, name: '', siteUrl: '', sourceUrl: '', description: '',
})
const isEditProject = computed(() => projectForm.id !== null)

function openCreateProject() {
  projectForm.id = null
  projectForm.name = ''
  projectForm.siteUrl = ''
  projectForm.sourceUrl = ''
  projectForm.description = ''
  showProjectModal.value = true
}

function openEditProject(row: ProfileProject) {
  projectForm.id = row.id
  projectForm.name = row.name
  projectForm.siteUrl = row.siteUrl || ''
  projectForm.sourceUrl = row.sourceUrl || ''
  projectForm.description = row.description || ''
  showProjectModal.value = true
}

async function handleSaveProject() {
  if (!projectForm.name.trim()) {
    message.warning('项目名不能为空')
    return
  }
  projectSaving.value = true
  const payload = {
    id: projectForm.id ?? undefined,
    name: projectForm.name.trim(),
    siteUrl: projectForm.siteUrl.trim() || null,
    sourceUrl: projectForm.sourceUrl.trim() || null,
    description: projectForm.description.trim() || null,
  }
  try {
    if (isEditProject.value) {
      await updateProfileProject(payload)
      message.success('已更新')
    } else {
      await createProfileProject(payload)
      message.success('已新增')
    }
    showProjectModal.value = false
    loadProjects()
  } catch {
    // 错误已由拦截器提示
  } finally {
    projectSaving.value = false
  }
}

async function handleDeleteProject(id: number) {
  try {
    await deleteProfileProject(id)
    message.success('删除成功')
    loadProjects()
  } catch {
    // 错误已由拦截器提示
  }
}

onMounted(() => {
  load()
  loadProjects()
})
</script>

<template>
  <div class="profile-admin-page">
    <NCard title="个人主页" :bordered="false">
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

    <NCard title="项目展示" :bordered="false">
      <template #header-extra>
        <NButton size="small" type="primary" @click="openCreateProject">新增项目</NButton>
      </template>
      <NDataTable
        :columns="projectColumns"
        :data="projects"
        :loading="projectLoading"
        :row-key="(row: ProfileProject) => row.id"
        :scroll-x="760"
        size="small"
      />
    </NCard>

    <NModal v-model:show="showProjectModal" preset="card" :title="isEditProject ? '编辑项目' : '新增项目'" style="width: 520px; max-width: 90vw">
      <NSpace vertical :size="16">
        <NFormItem label="项目名" required>
          <NInput v-model:value="projectForm.name" placeholder="如：数学游戏" :maxlength="100" />
        </NFormItem>
        <NFormItem label="线上地址">
          <NInput v-model:value="projectForm.siteUrl" placeholder="https://example.com" :maxlength="500" />
        </NFormItem>
        <NFormItem label="源码地址">
          <NInput v-model:value="projectForm.sourceUrl" placeholder="https://github.com/xxx/xxx" :maxlength="500" />
        </NFormItem>
        <NFormItem label="简介">
          <NInput
            v-model:value="projectForm.description"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 5 }"
            placeholder="1-2 句项目简介"
            :maxlength="500"
          />
        </NFormItem>
        <NSpace justify="end">
          <NButton @click="showProjectModal = false">取消</NButton>
          <NButton type="primary" :loading="projectSaving" @click="handleSaveProject">保存</NButton>
        </NSpace>
      </NSpace>
    </NModal>
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
  border: 1px solid var(--c-border);
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
