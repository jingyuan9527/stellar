<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NCard, NButton, NImage, NSpace, NTag, NGrid, NGridItem } from 'naive-ui'
import { getPublicProfile, getPublicProfileProjects } from '@/api/profile'
import type { Profile, ProfileProject } from '@/types/api'

const profile = ref<Profile | null>(null)
const projects = ref<ProfileProject[]>([])

const skills = computed(() =>
  profile.value?.skills
    ? profile.value.skills.split(',').map((s) => s.trim()).filter(Boolean)
    : [],
)

interface LinkItem { key: string; label: string; href: string }
const linkItems = computed<LinkItem[]>(() => {
  if (!profile.value?.links) return []
  let obj: Record<string, string> = {}
  try {
    obj = JSON.parse(profile.value.links)
  } catch {
    return []
  }
  const labelMap: Record<string, string> = {
    github: 'GitHub', email: '邮箱', site: '个人站点',
    wechat: '微信', weibo: '微博', x: 'X', twitter: 'Twitter',
  }
  return Object.entries(obj)
    .filter(([, v]) => v)
    .map(([k, v]) => ({
      key: k,
      label: labelMap[k] || k,
      href: k === 'email' ? `mailto:${v}` : v,
    }))
})

// 项目卡片主题色：brand/info 两色交替（跟随主题色），用于卡片左侧色条 + hover 边框
function projectAccent(idx: number): string {
  return idx % 2 === 0 ? 'var(--c-brand)' : 'var(--c-info)'
}

async function load() {
  try {
    profile.value = await getPublicProfile()
  } catch {
    // 错误已由拦截器提示
  }
}

async function loadProjects() {
  try {
    projects.value = await getPublicProfileProjects()
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
  <div class="about-page">
    <!-- Hero -->
    <NCard class="hero-card" :bordered="false">
      <div class="hero-glow hero-glow-a" />
      <div class="hero-glow hero-glow-b" />
      <div class="hero">
        <div class="hero-avatar">
          <NImage
            v-if="profile?.avatar"
            :src="profile.avatar"
            width="120"
            height="120"
            object-fit="cover"
            round
            preview-disabled
          />
          <div v-else class="avatar-placeholder">{{ (profile?.nickname || 'S').slice(0, 1) }}</div>
        </div>
        <div class="hero-text">
          <h1 class="hero-name">{{ profile?.nickname || 'Stellar' }}</h1>
          <p v-if="profile?.title" class="hero-title">{{ profile.title }}</p>
          <p v-if="profile?.bio" class="hero-bio">{{ profile.bio }}</p>
          <NSpace v-if="linkItems.length" :size="8" class="hero-links">
            <NButton
              v-for="l in linkItems"
              :key="l.key"
              size="small"
              secondary
              tag="a"
              :href="l.href"
              :target="l.key === 'email' ? undefined : '_blank'"
            >{{ l.label }}</NButton>
          </NSpace>
        </div>
      </div>
    </NCard>

    <!-- 关于我 -->
    <section v-if="profile?.about" class="section">
      <h2 class="section-title">关于我</h2>
      <NCard :bordered="false">
        <div class="about-content" v-html="profile.about"></div>
      </NCard>
    </section>

    <!-- 技能 -->
    <section v-if="skills.length" class="section">
      <h2 class="section-title">技能栈</h2>
      <NCard :bordered="false">
        <NSpace :size="8">
          <NTag
            v-for="t in skills"
            :key="t"
            size="medium"
            :bordered="false"
          >{{ t }}</NTag>
        </NSpace>
      </NCard>
    </section>

    <!-- 项目展示 -->
    <section v-if="projects.length" class="section">
      <h2 class="section-title">项目展示</h2>
      <NGrid :x-gap="16" :y-gap="16" :cols="2" responsive="screen" item-responsive>
        <NGridItem
          v-for="(p, idx) in projects"
          :key="p.id"
          span="2 m:1"
        >
          <NCard :bordered="false" class="project-card" :style="{ '--project-accent': projectAccent(idx), animationDelay: idx * 40 + 'ms' }">
            <div class="project-body">
              <div class="project-head">
                <h3 class="project-name">{{ p.name }}</h3>
                <p v-if="p.description" class="project-desc">{{ p.description }}</p>
              </div>
              <NSpace v-if="p.siteUrl || p.sourceUrl" :size="8" class="project-actions">
                <NButton
                  v-if="p.siteUrl"
                  size="small"
                  type="primary"
                  tag="a"
                  :href="p.siteUrl"
                  target="_blank"
                >访问线上</NButton>
                <NButton
                  v-if="p.sourceUrl"
                  size="small"
                  secondary
                  tag="a"
                  :href="p.sourceUrl"
                  target="_blank"
                >查看源码</NButton>
              </NSpace>
            </div>
          </NCard>
        </NGridItem>
      </NGrid>
    </section>

    <!-- 联系方式 -->
    <section v-if="profile?.location || linkItems.length" class="section">
      <h2 class="section-title">联系方式</h2>
      <NCard :bordered="false">
        <div class="contact">
          <p v-if="profile?.location" class="contact-item">📍 所在地：{{ profile.location }}</p>
          <NSpace v-if="linkItems.length" :size="8">
            <NButton
              v-for="l in linkItems"
              :key="l.key"
              size="small"
              secondary
              tag="a"
              :href="l.href"
              :target="l.key === 'email' ? undefined : '_blank'"
            >{{ l.label }}</NButton>
          </NSpace>
        </div>
      </NCard>
    </section>
  </div>
</template>

<style scoped>
.about-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}

.hero-card {
  --n-color: transparent;
  position: relative;
  overflow: hidden;
  border: 1px solid var(--c-border);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--c-brand) 10%, var(--c-fill)),
    color-mix(in srgb, var(--c-info) 10%, var(--c-fill))
  );
}

.hero {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 24px;
}

.hero-glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(48px);
  opacity: 0.5;
}

.hero-glow-a {
  width: 320px;
  height: 320px;
  top: -140px;
  right: -100px;
  background: radial-gradient(circle, color-mix(in srgb, var(--c-brand) 30%, transparent), transparent 70%);
}

.hero-glow-b {
  width: 260px;
  height: 260px;
  bottom: -120px;
  left: -80px;
  background: radial-gradient(circle, color-mix(in srgb, var(--c-info) 30%, transparent), transparent 70%);
}

.hero-avatar {
  flex-shrink: 0;
}

.avatar-placeholder {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48px;
  font-weight: 800;
  background: var(--c-fill-2);
}

.hero-name {
  font-size: 32px;
  font-weight: 800;
  margin: 0 0 4px;
}

.hero-title {
  font-size: 16px;
  color: var(--c-text-2);
  margin: 0 0 8px;
}

.hero-bio {
  font-size: 14px;
  color: var(--c-text-3);
  margin: 0 0 12px;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.about-content {
  line-height: 1.8;
}

.about-content :deep(p) {
  margin: 0 0 12px;
}

.about-content :deep(h3) {
  margin: 16px 0 8px;
}

.contact-item {
  margin: 0 0 12px;
  font-size: 14px;
}

.project-card {
  height: 100%;
  overflow: hidden;
  border-left: 4px solid var(--project-accent, var(--c-brand));
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  animation: list-in 0.3s ease both;
}

.project-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--sh-card);
}

@media (prefers-reduced-motion: reduce) {
  .project-card {
    transition: none;
  }
  .project-card:hover {
    transform: none;
  }
}

.project-card :deep(.n-card__content) {
  padding: 18px 20px;
}

.project-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  gap: 12px;
  min-height: 120px;
}

.project-head {
  flex: 1;
}

.project-name {
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 6px;
  line-height: 1.4;
}

.project-desc {
  margin: 0;
  font-size: 13px;
  color: var(--c-text-2);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.project-actions {
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .hero {
    flex-direction: column;
    text-align: center;
  }
  .hero-links {
    justify-content: center;
  }
  .hero-name {
    font-size: 26px;
  }
}
</style>
