<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { NCard, NDataTable, NSwitch, NButton, NSpace, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { RouteRecordRaw } from 'vue-router'
import { routes } from '@/router'
import { getMenuVisibilityList, batchUpdateMenuVisibility } from '@/api/menu-visibility'
import type { MenuVisibilityItem } from '@/types/api'

const message = useMessage()

interface Row extends MenuVisibilityItem {
  parentName?: string
}

const loading = ref(false)
const saving = ref(false)
const tableData = ref<Row[]>([])

function joinPath(parent: string, child: string) {
  if (child.startsWith('/')) return child
  const base = parent.endsWith('/') ? parent.slice(0, -1) : parent
  return `${base}/${child}`
}

/** 提取所有叶子路由（无可见子项），排除天然公开（requiresAuth===false）与 hidden */
function extractLeafRoutes(): Row[] {
  const result: Row[] = []
  const walk = (records: RouteRecordRaw[], parentPath: string, parentName?: string) => {
    for (const r of records) {
      if (r.meta?.hidden) continue
      const fullPath = joinPath(parentPath, r.path)
      const visibleChildren = r.children?.filter((c) => !c.meta?.hidden) ?? []
      if (visibleChildren.length > 0) {
        walk(r.children!, fullPath, (r.meta?.title as string) || parentName)
      } else {
        if (r.meta?.requiresAuth === false) continue
        result.push({
          routeKey: fullPath,
          routeName: (r.meta?.title as string) || String(r.name) || r.path,
          parentKey: parentPath || null,
          parentName,
          publicVisible: 0,
          sortOrder: 0,
        })
      }
    }
  }
  const root = routes.find((r) => r.name === 'Root')
  if (root?.children) walk(root.children, '')
  return result
}

async function loadData() {
  loading.value = true
  try {
    const list = await getMenuVisibilityList()
    const configMap = new Map(list.map((v) => [v.routeKey, v]))
    tableData.value = extractLeafRoutes().map((r) => {
      const cfg = configMap.get(r.routeKey)
      return {
        ...r,
        publicVisible: cfg?.publicVisible ?? 0,
        sortOrder: cfg?.sortOrder ?? 0,
      }
    })
  } catch {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    await batchUpdateMenuVisibility(
      tableData.value.map((r) => ({
        routeKey: r.routeKey,
        routeName: r.routeName,
        parentKey: r.parentKey,
        publicVisible: r.publicVisible,
        sortOrder: r.sortOrder,
      })),
    )
    message.success('已保存，游客侧菜单将在下次加载时生效')
  } catch {
    // 错误已由拦截器提示
  } finally {
    saving.value = false
  }
}

const columns: DataTableColumns<Row> = [
  { title: '路由名称', key: 'routeName', width: 180 },
  {
    title: '路径', key: 'routeKey', width: 220,
    render: (row) => h('span', { style: 'opacity: 0.6; font-size: 12px' }, row.routeKey),
  },
  {
    title: '父菜单', key: 'parentName', width: 140,
    render: (row) => row.parentName || '-',
  },
  {
    title: '对游客公开', key: 'publicVisible', width: 120,
    render: (row) =>
      h(NSwitch, {
        value: row.publicVisible === 1,
        onUpdateValue: (v: boolean) => {
          row.publicVisible = v ? 1 : 0
        },
      }),
  },
]

onMounted(loadData)
</script>

<template>
  <div class="menu-visibility-page">
    <NCard title="游客访问配置" :bordered="false">
      <template #header-extra>
        <NButton type="primary" :loading="saving" @click="handleSave">保存配置</NButton>
      </template>
      <NSpace style="margin-bottom: 16px">
        <span style="opacity: 0.65; font-size: 13px">
          勾选对游客公开的菜单，保存后游客可在侧边栏看到并访问对应页面（受 IP 单日限流保护，阶段 3 生效）。
          天然公开的首页无需配置；管理类页面不建议公开。
        </span>
      </NSpace>
      <NDataTable
        :columns="columns"
        :data="tableData"
        :loading="loading"
        :row-key="(row: Row) => row.routeKey"
        :pagination="false"
        :bordered="false"
      />
    </NCard>
  </div>
</template>

<style scoped>
.menu-visibility-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
