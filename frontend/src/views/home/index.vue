<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NCard, NButton } from 'naive-ui'
import { useAuthStore } from '@/store/auth'
import { useMenuStore } from '@/store/menu'
import { routes } from '@/router'
import type { RouteRecordRaw } from 'vue-router'
import { getPublicProfile } from '@/api/profile'
import type { Profile } from '@/types/api'

const router = useRouter()
const authStore = useAuthStore()
const menuStore = useMenuStore()
const profile = ref<Profile | null>(null)

function joinPath(parent: string, child: string) {
  if (child.startsWith('/')) return child
  const base = parent.endsWith('/') ? parent.slice(0, -1) : parent
  return `${base}/${child}`
}

/** 公开工具页卡片：从路由叶子提取 publicKeys 命中的项 */
const cards = computed(() => {
  const keys = new Set(menuStore.publicKeys)
  const result: { key: string; title: string }[] = []
  const walk = (records: RouteRecordRaw[], parentPath: string) => {
    for (const r of records) {
      if (r.meta?.hidden) continue
      const fullPath = joinPath(parentPath, r.path)
      const visibleChildren = r.children?.filter((c) => !c.meta?.hidden) ?? []
      if (visibleChildren.length > 0) {
        walk(r.children!, fullPath)
      } else if (keys.has(fullPath)) {
        result.push({
          key: fullPath,
          title: (r.meta?.title as string) || String(r.name),
        })
      }
    }
  }
  const root = routes.find((r) => r.name === 'Root')
  if (root?.children) walk(root.children, '')
  return result
})

const skills = computed(() =>
  profile.value?.skills
    ? profile.value.skills.split(',').map((s) => s.trim()).filter(Boolean)
    : [],
)

onMounted(async () => {
  if (!authStore.isLogin) {
    await menuStore.loadPublicConfig()
  }
  try {
    profile.value = await getPublicProfile()
  } catch {
    // 游客访问公开 profile，不应失败；失败则用默认
  }
})
</script>

<template>
  <div class="home-page">
    <div class="hero">
      <h1 class="title">{{ profile?.nickname || 'Stellar' }}</h1>
      <p class="bio">{{ profile?.bio || '个人知识 / 实验沉淀池' }}</p>
      <div v-if="skills.length" class="skills">
        <span v-for="t in skills" :key="t" class="skill">{{ t }}</span>
      </div>
    </div>

    <div class="section">
      <h2 class="section-title">工具与实验</h2>
      <div v-if="cards.length" class="cards-grid">
        <NCard
          v-for="c in cards"
          :key="c.key"
          class="nav-card"
          hoverable
          :bordered="false"
          @click="router.push(c.key)"
        >
          <div class="nav-body">
            <span class="nav-title">{{ c.title }}</span>
            <span class="nav-arrow">→</span>
          </div>
        </NCard>
      </div>
      <p v-else class="empty-tip">暂未公开工具。登录后可在"游客访问配置"中开放。</p>
    </div>

    <div class="section section-entries">
      <NButton size="large" type="primary" @click="router.push('/about')">
        关于我 →
      </NButton>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: 28px;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}

.title {
  font-size: 36px;
  font-weight: 800;
  margin: 0 0 8px;
  letter-spacing: 1px;
}

.bio {
  font-size: 16px;
  opacity: 0.7;
  margin: 0 0 16px;
}

.skills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.skill {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(127, 127, 127, 0.14);
}

.section-title {
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 14px;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}

.nav-card {
  cursor: pointer;
}

.nav-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.nav-title {
  font-size: 15px;
  font-weight: 600;
}

.nav-arrow {
  opacity: 0.4;
}

.empty-tip {
  font-size: 13px;
  opacity: 0.6;
}

.section-entries {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .title { font-size: 26px; }
  .cards-grid { grid-template-columns: 1fr; }
}
</style>
