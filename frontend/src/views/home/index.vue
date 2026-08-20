<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NTag, NIcon } from 'naive-ui'
import { useAuthStore } from '@/store/auth'
import { useMenuStore } from '@/store/menu'
import { routes } from '@/router'
import type { RouteRecordRaw } from 'vue-router'
import { getPublicProfile } from '@/api/profile'
import type { Profile } from '@/types/api'
import { iconMap } from '@/utils/icons'

const router = useRouter()
const authStore = useAuthStore()
const menuStore = useMenuStore()
const profile = ref<Profile | null>(null)

function joinPath(parent: string, child: string) {
  if (child.startsWith('/')) return child
  const base = parent.endsWith('/') ? parent.slice(0, -1) : parent
  return `${base}/${child}`
}

interface ToolItem {
  key: string
  title: string
  icon?: string
  description?: string
}

/** 工具卡分组：从路由提取带 description 的公开叶子，按一级类目分组 */
const toolGroups = computed(() => {
  const keys = new Set(menuStore.publicKeys)
  const groups: Record<string, ToolItem[]> = {}
  const walk = (records: RouteRecordRaw[], parentPath: string, category: string | undefined, isTopLevel: boolean) => {
    for (const r of records) {
      if (r.meta?.hidden) continue
      const fullPath = joinPath(parentPath, r.path)
      const visibleChildren = r.children?.filter((c) => !c.meta?.hidden) ?? []
      const cat = isTopLevel ? r.meta?.title : category
      if (visibleChildren.length > 0) {
        walk(r.children!, fullPath, cat, false)
      } else if (r.meta?.description) {
        const isPublic = r.meta?.requiresAuth === false || keys.has(fullPath)
        if (isPublic) {
          const g = cat || '工具'
          ;(groups[g] ??= []).push({
            key: fullPath,
            title: r.meta.title as string,
            icon: r.meta.icon as string,
            description: r.meta.description as string,
          })
        }
      }
    }
  }
  const root = routes.find((r) => r.name === 'Root')
  if (root?.children) walk(root.children, '', undefined, true)
  return Object.entries(groups).map(([category, items]) => ({ category, items }))
})

const skills = computed(() =>
  profile.value?.skills
    ? profile.value.skills.split(',').map((s) => s.trim()).filter(Boolean)
    : [],
)

/** 社交链接：与 about 页同一解析逻辑（links 为 {key: url} JSON） */
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

/** 页脚技术栈 */
const techStack = ['Vue 3', 'Vite', 'UnoCSS', 'Naive UI', 'Spring Boot 3', 'PostgreSQL', 'Redis', 'Sa-Token']

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
    <!-- Hero -->
    <section class="hero">
      <div class="hero-glow hero-glow-a" />
      <div class="hero-glow hero-glow-b" />
      <div class="hero-inner">
        <p class="hero-eyebrow">个人知识沉淀池</p>
        <h1 class="hero-title">{{ profile?.nickname || 'Stellar' }}</h1>
        <p class="hero-subtitle">{{ profile?.bio || '把灵感、实验与工具沉淀下来，随时取用。' }}</p>
        <div v-if="skills.length" class="hero-skills">
          <NTag v-for="t in skills" :key="t" size="small" round :bordered="false">{{ t }}</NTag>
        </div>
        <div class="hero-actions">
          <NButton size="large" round quaternary @click="router.push('/about')">
            关于我
          </NButton>
        </div>
      </div>
    </section>

    <!-- 工具区 -->
    <section id="tools" class="tools">
      <template v-if="toolGroups.length">
        <div v-for="(group, gi) in toolGroups" :key="group.category" class="tool-group">
          <h2 class="group-title">{{ group.category }}</h2>
          <div class="cards-grid">
            <div
              v-for="(c, ci) in group.items"
              :key="c.key"
              class="tool-card"
              :style="{ animationDelay: `${(gi * 3 + ci) * 60}ms` }"
              @click="router.push(c.key)"
            >
              <div class="tool-card-icon">
                <NIcon size="22">
                  <component :is="iconMap[c.icon || 'grid']" />
                </NIcon>
              </div>
              <div class="tool-card-body">
                <div class="tool-card-title">{{ c.title }}</div>
                <div class="tool-card-desc">{{ c.description }}</div>
              </div>
              <NIcon size="18" class="tool-card-arrow">
                <component :is="iconMap.arrow" />
              </NIcon>
            </div>
          </div>
        </div>
      </template>
      <p v-else class="empty-tip">暂未公开工具。登录后可在"游客访问配置"中开放。</p>
    </section>

    <!-- 页脚 -->
    <footer class="footer">
      <div class="footer-line">
        <span class="footer-copyright">© {{ new Date().getFullYear() }} Stellar</span>
        <span v-if="linkItems.length" class="footer-links">
          <a
            v-for="l in linkItems"
            :key="l.key"
            :href="l.href"
            target="_blank"
            rel="noopener"
            class="footer-link"
          >
            {{ l.label }}
          </a>
        </span>
      </div>
      <div class="footer-stack">
        <span v-for="t in techStack" :key="t" class="footer-chip">{{ t }}</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.home-page {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 48px;
}

/* ===== Hero ===== */
.hero {
  position: relative;
  overflow: hidden;
  border-radius: var(--r-lg);
  padding: 72px 32px 64px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--c-brand) 10%, var(--c-bg)),
    color-mix(in srgb, var(--c-info) 10%, var(--c-bg))
  );
  border: 1px solid var(--c-border);
  text-align: center;
}

.hero-glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(48px);
  opacity: 0.5;
}

.hero-glow-a {
  width: 360px;
  height: 360px;
  top: -140px;
  right: -100px;
  background: radial-gradient(circle, color-mix(in srgb, var(--c-brand) 30%, transparent), transparent 70%);
}

.hero-glow-b {
  width: 300px;
  height: 300px;
  bottom: -120px;
  left: -80px;
  background: radial-gradient(circle, color-mix(in srgb, var(--c-info) 30%, transparent), transparent 70%);
}

.hero-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.hero-eyebrow {
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: var(--c-brand);
  animation: fadeUp 0.6s ease both;
}

.hero-title {
  margin: 0 0 16px;
  font-size: clamp(40px, 6vw, 56px);
  font-weight: 800;
  letter-spacing: 1px;
  line-height: 1.15;
  animation: fadeUp 0.6s ease 0.1s both;
}

.hero-subtitle {
  margin: 0 0 20px;
  font-size: 17px;
  color: var(--c-text-2);
  max-width: 520px;
  animation: fadeUp 0.6s ease 0.2s both;
}

.hero-skills {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-bottom: 28px;
  animation: fadeUp 0.6s ease 0.3s both;
}

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
  animation: fadeUp 0.6s ease 0.4s both;
}

/* ===== 工具区 ===== */
.tools {
  display: flex;
  flex-direction: column;
  gap: 36px;
}

.tool-group {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.group-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  padding-left: 12px;
  border-left: 4px solid var(--c-brand);
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}

.tool-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border-radius: var(--r-md);
  background: var(--c-fill);
  border: 1px solid var(--c-border);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  animation: fadeUp 0.5s ease both;
}

.tool-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--sh-card);
  border-color: var(--c-brand);
}

.tool-card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: var(--r-sm);
  background: var(--c-fill-2);
  color: var(--c-brand);
  flex-shrink: 0;
}

.tool-card-body {
  flex: 1;
  min-width: 0;
}

.tool-card-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 2px;
}

.tool-card-desc {
  font-size: 12px;
  color: var(--c-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-card-arrow {
  color: var(--c-text-3);
  flex-shrink: 0;
  transition: transform 0.2s ease;
}

.tool-card:hover .tool-card-arrow {
  transform: translateX(3px);
  color: var(--c-brand);
}

.empty-tip {
  font-size: 13px;
  color: var(--c-text-3);
}

/* ===== 页脚 ===== */
.footer {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 28px;
  border-top: 1px solid var(--c-border);
}

.footer-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.footer-copyright {
  font-size: 13px;
  color: var(--c-text-2);
}

.footer-links {
  display: flex;
  gap: 16px;
}

.footer-link {
  font-size: 13px;
  color: var(--c-text-2);
  transition: color 0.2s;
}

.footer-link:hover {
  color: var(--c-info);
}

.footer-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.footer-chip {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--c-fill-2);
  color: var(--c-text-3);
}

/* ===== 入场动画 ===== */
@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 移动端 */
@media (max-width: 768px) {
  .home-page {
    gap: 32px;
  }
  .hero {
    padding: 48px 20px 44px;
  }
  .hero-title {
    font-size: clamp(30px, 8vw, 40px);
  }
  .cards-grid {
    grid-template-columns: 1fr;
  }
  .footer-line {
    flex-direction: column;
    align-items: flex-start;
  }
}

/* 减弱动效偏好 */
@media (prefers-reduced-motion: reduce) {
  .hero-eyebrow,
  .hero-title,
  .hero-subtitle,
  .hero-skills,
  .hero-actions,
  .tool-card {
    animation: none;
  }
  .tool-card,
  .tool-card-arrow {
    transition: none;
  }
}
</style>