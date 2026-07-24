<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import {
  NCard, NEmpty, NGrid, NGridItem, NModal, NSpace, NTag, NImage, NButton,
} from 'naive-ui'
import type { Showcase } from '@/types/api'
import { useIsMobile } from '@/composables/useBreakpoint'
import { getPublicShowcase } from '@/api/showcase'
import { getPublicProfile } from '@/api/profile'
import type { Profile } from '@/types/api'

const isMobile = useIsMobile()
const loading = ref(false)
const list = ref<Showcase[]>([])
const profile = ref<Profile | null>(null)
const detail = ref<Showcase | null>(null)
const detailShow = ref(false)

const typeLabel: Record<string, string> = {
  cover: '封面', text: '文案', audio: '音频', demo: 'Demo', project: '项目', link: '链接',
}
const typeColor: Record<string, 'default' | 'success' | 'info' | 'warning' | 'error'> = {
  cover: 'success', text: 'info', audio: 'warning', demo: 'error', project: 'success', link: 'default',
}

const cols = computed(() => (isMobile.value ? 1 : 3))

function tagsOf(s: Showcase) {
  return s.tags ? s.tags.split(',').map((t) => t.trim()).filter(Boolean) : []
}

function openDetail(s: Showcase) {
  detail.value = s
  detailShow.value = true
}

async function loadData() {
  loading.value = true
  try {
    const [s, p] = await Promise.all([getPublicShowcase(), getPublicProfile()])
    list.value = s
    profile.value = p
  } catch {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="showcase-page">
    <NCard class="intro-card" :bordered="false">
      <div class="intro">
        <h1 class="intro-title">{{ profile?.nickname || 'Stellar' }}</h1>
        <p class="intro-bio">{{ profile?.bio || '个人知识 / 实验沉淀池' }}</p>
        <NSpace v-if="profile?.skills" :size="8" class="intro-skills">
          <NTag
            v-for="t in profile.skills.split(',').map((s) => s.trim()).filter(Boolean)"
            :key="t"
            size="small"
            :bordered="false"
          >{{ t }}</NTag>
        </NSpace>
      </div>
    </NCard>

    <h2 class="section-title">作品橱窗</h2>
    <NEmpty v-if="!loading && list.length === 0" description="暂无作品" />
    <NGrid v-else :cols="cols" :x-gap="16" :y-gap="16">
      <NGridItem v-for="s in list" :key="s.id">
        <NCard class="case-card" hoverable :bordered="false" @click="openDetail(s)">
          <div class="cover">
            <NImage
              v-if="s.coverUrl"
              :src="s.coverUrl"
              object-fit="cover"
              class="cover-img"
              preview-disabled
            />
            <div v-else class="cover-placeholder">{{ typeLabel[s.type] || '作品' }}</div>
          </div>
          <div class="card-body">
            <NSpace justify="space-between" align="center">
              <NTag size="small" :type="typeColor[s.type] || 'default'" :bordered="false">
                {{ typeLabel[s.type] || s.type }}
              </NTag>
              <span class="card-date">{{ s.createTime?.slice(0, 10) }}</span>
            </NSpace>
            <h3 class="card-title">{{ s.title }}</h3>
            <p v-if="s.summary" class="card-summary">{{ s.summary }}</p>
            <div v-if="tagsOf(s).length" class="card-tags">
              <NTag
                v-for="t in tagsOf(s)"
                :key="t"
                size="small"
                :bordered="false"
              >#{{ t }}</NTag>
            </div>
          </div>
        </NCard>
      </NGridItem>
    </NGrid>

    <NModal
      v-model:show="detailShow"
      preset="card"
      :title="detail?.title"
      :style="{ width: '680px', maxWidth: '92vw' }"
      @after-leave="detail = null"
    >
      <div v-if="detail" class="detail">
        <NImage
          v-if="detail.coverUrl"
          :src="detail.coverUrl"
          object-fit="contain"
          class="detail-cover"
        />
        <p v-if="detail.summary" class="detail-summary">{{ detail.summary }}</p>
        <div v-if="detail.content" class="detail-content" v-html="detail.content"></div>
        <audio
          v-if="detail.type === 'audio' && detail.mediaUrl"
          :src="detail.mediaUrl"
          controls
          class="detail-audio"
        ></audio>
        <NButton
          v-if="detail.link"
          tag="a"
          :href="detail.link"
          target="_blank"
          type="primary"
        >访问链接</NButton>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.showcase-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.intro-card {
  background: var(--n-color, transparent);
}

.intro-title {
  font-size: 28px;
  font-weight: 800;
  margin: 0 0 8px;
}

.intro-bio {
  font-size: 15px;
  opacity: 0.7;
  margin: 0 0 12px;
}

.section-title {
  font-size: 18px;
  font-weight: 700;
  margin: 8px 0 0;
}

.case-card {
  overflow: hidden;
  cursor: pointer;
}

.case-card :deep(.n-card__content) {
  padding: 0;
}

.cover {
  width: 100%;
  height: 180px;
  background: rgba(127, 127, 127, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-img {
  width: 100%;
  height: 100%;
}

.cover-placeholder {
  font-size: 18px;
  opacity: 0.5;
}

.card-body {
  padding: 12px 14px;
}

.card-date {
  font-size: 12px;
  opacity: 0.5;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  margin: 8px 0 6px;
}

.card-summary {
  font-size: 13px;
  opacity: 0.65;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 8px;
}

.detail-cover {
  width: 100%;
  max-height: 360px;
  margin-bottom: 12px;
}

.detail-summary {
  opacity: 0.75;
  margin: 0 0 12px;
}

.detail-content {
  line-height: 1.7;
  margin-bottom: 12px;
}

.detail-audio {
  width: 100%;
  margin-bottom: 12px;
}

@media (max-width: 768px) {
  .intro-title { font-size: 22px; }
  .cover { height: 150px; }
}
</style>
