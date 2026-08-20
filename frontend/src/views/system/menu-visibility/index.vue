<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NCard, NButton, NSpace, NSwitch, NIcon, useMessage } from 'naive-ui'
import type { RouteRecordRaw } from 'vue-router'
import { routes } from '@/router'
import { getMenuVisibilityList, batchUpdateMenuVisibility } from '@/api/menu-visibility'
import type { MenuVisibilityItem } from '@/types/api'
import { iconMap } from '@/utils/icons'
import SkeletonList from '@/components/SkeletonList.vue'

const message = useMessage()

interface Row extends MenuVisibilityItem {
  parentName?: string
}

interface Group {
  name: string
  icon?: string
  items: Row[]
}

const loading = ref(false)
const saving = ref(false)
const groups = ref<Group[]>([])

function joinPath(parent: string, child: string) {
  if (child.startsWith('/')) return child
  const base = parent.endsWith('/') ? parent.slice(0, -1) : parent
  return `${base}/${child}`
}

/** 按一级菜单分组提取叶子路由（排除天然公开与 hidden） */
function buildGroups(): Group[] {
  const groupsMap = new Map<string, Group>()
  const root = routes.find((r) => r.name === 'Root')
  if (!root?.children) return []
  const walk = (records: RouteRecordRaw[], parentPath: string, parentName: string, parentIcon?: string) => {
    for (const r of records) {
      if (r.meta?.hidden) continue
      const fullPath = joinPath(parentPath, r.path)
      const visibleChildren = r.children?.filter((c) => !c.meta?.hidden) ?? []
      if (visibleChildren.length > 0) {
        walk(r.children!, fullPath, (r.meta?.title as string) || parentName, r.meta?.icon || parentIcon)
      } else {
        if (r.meta?.requiresAuth === false) continue
        const group = groupsMap.get(parentPath) ?? {
          name: parentName || '顶级',
          icon: parentIcon,
          items: [] as Row[],
        }
        group.items.push({
          routeKey: fullPath,
          routeName: (r.meta?.title as string) || String(r.name) || r.path,
          parentKey: parentPath || null,
          parentName,
          publicVisible: 0,
          sortOrder: 0,
        })
        groupsMap.set(parentPath, group)
      }
    }
  }
  walk(root.children, '', '顶级')
  return [...groupsMap.values()].filter((g) => g.items.length > 0)
}

async function loadData() {
  loading.value = true
  try {
    const list = await getMenuVisibilityList()
    const configMap = new Map(list.map((v) => [v.routeKey, v]))
    groups.value = buildGroups().map((g) => ({
      ...g,
      items: g.items.map((r) => {
        const cfg = configMap.get(r.routeKey)
        return {
          ...r,
          publicVisible: cfg?.publicVisible ?? 0,
          sortOrder: cfg?.sortOrder ?? 0,
        }
      }),
    }))
  } catch {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

const allPublic = computed(() => groups.value.every((g) => g.items.every((i) => i.publicVisible === 1)))

function toggleAll() {
  const next = !allPublic.value
  groups.value.forEach((g) => g.items.forEach((i) => (i.publicVisible = next ? 1 : 0)))
}

function toggleGroup(group: Group) {
  const allOn = group.items.every((i) => i.publicVisible === 1)
  group.items.forEach((i) => (i.publicVisible = allOn ? 0 : 1))
}

async function handleSave() {
  saving.value = true
  try {
    const items = groups.value.flatMap((g) =>
      g.items.map((r) => ({
        routeKey: r.routeKey,
        routeName: r.routeName,
        parentKey: r.parentKey,
        publicVisible: r.publicVisible,
        sortOrder: r.sortOrder,
      })),
    )
    await batchUpdateMenuVisibility(items)
    message.success('已保存，游客侧菜单将在下次加载时生效')
  } catch {
    // 错误已由拦截器提示
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="menu-visibility-page">
    <NCard title="游客访问配置" :bordered="false">
      <template #header-extra>
        <NSpace>
          <NButton :loading="saving" @click="toggleAll">
            {{ allPublic ? '全部关闭' : '全部公开' }}
          </NButton>
          <NButton type="primary" :loading="saving" @click="handleSave">保存配置</NButton>
        </NSpace>
      </template>
      <NSpace style="margin-bottom: 16px">
        <span style="color: var(--c-text-2); font-size: 13px">
          按一级菜单分组，勾选对游客公开的页面。天然公开的首页/聊天/图片等无需配置，不会出现在此列表。
          管理类页面不建议公开。
        </span>
      </NSpace>
      <div v-if="loading" class="loading"><SkeletonList :rows="3" height="56px" /></div>
      <div v-else class="group-grid">
        <NCard
          v-for="group in groups"
          :key="group.name"
          :bordered="true"
          size="small"
          class="group-card"
        >
          <template #header>
            <NSpace align="center" size="small">
              <NIcon v-if="group.icon" size="18" style="color: var(--c-text-3)">
                <component :is="iconMap[group.icon]" />
              </NIcon>
              <span class="group-name">{{ group.name }}</span>
              <span class="group-count">{{ group.items.length }} 项</span>
            </NSpace>
          </template>
          <template #header-extra>
            <NButton size="tiny" quaternary type="primary" @click="toggleGroup(group)">
              {{ group.items.every((i) => i.publicVisible === 1) ? '全部关闭' : '全部公开' }}
            </NButton>
          </template>
          <div class="group-items">
            <div
              v-for="row in group.items"
              :key="row.routeKey"
              class="group-item"
              :class="{ public: row.publicVisible === 1 }"
            >
              <div class="item-info">
                <span class="item-name">{{ row.routeName }}</span>
                <span class="item-path">{{ row.routeKey }}</span>
              </div>
              <NSwitch
                size="small"
                :value="row.publicVisible === 1"
                @update:value="(v: boolean) => (row.publicVisible = v ? 1 : 0)"
              />
            </div>
          </div>
        </NCard>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.menu-visibility-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.loading {
  padding: 16px 0;
}

.group-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.group-name {
  font-weight: 600;
}

.group-count {
  font-size: 12px;
  color: var(--c-text-3);
}

.group-items {
  display: flex;
  flex-direction: column;
}

.group-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 4px;
  border-bottom: 1px solid rgba(128, 128, 128, 0.12);
}

.group-item:last-child {
  border-bottom: none;
}

.group-item.public .item-name {
  color: var(--c-success);
}

.item-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.item-name {
  font-size: 14px;
}

.item-path {
  font-size: 12px;
  color: var(--c-text-3);
  word-break: break-all;
}

@media (max-width: 768px) {
  .group-grid {
    grid-template-columns: 1fr;
  }
}
</style>