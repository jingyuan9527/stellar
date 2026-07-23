import { h } from 'vue'
import { NIcon } from 'naive-ui'
import type { MenuOption, MenuGroupOption } from 'naive-ui'
import type { RouteRecordRaw } from 'vue-router'
import { routes } from '@/router'
import { iconMap } from '@/utils/icons'

function renderIcon(iconName?: string) {
  if (!iconName) return undefined
  const Icon = iconMap[iconName]
  if (!Icon) return undefined
  return () => h(NIcon, null, { default: () => h(Icon) })
}

function joinPath(parent: string, child: string) {
  if (child.startsWith('/')) return child
  const base = parent.endsWith('/') ? parent.slice(0, -1) : parent
  return `${base}/${child}`
}

function buildMenu(routeRecords: RouteRecordRaw[], parentPath: string): MenuOption[] {
  return routeRecords
    .filter((r) => !r.meta?.hidden)
    .sort((a, b) => (a.meta?.order ?? 0) - (b.meta?.order ?? 0))
    .map((r) => {
      const fullPath = joinPath(parentPath, r.path)
      const hasChildren = !!r.children && r.children.filter((c) => !c.meta?.hidden).length > 0
      const option: MenuOption = {
        label: r.meta?.title || r.name || r.path,
        key: fullPath,
        icon: renderIcon(r.meta?.icon),
      }
      if (hasChildren && r.children) {
        option.children = buildMenu(r.children, fullPath)
      }
      return option
    })
}

export function generateMenus(): Array<MenuOption | MenuGroupOption> {
  const root = routes.find((r) => r.name === 'Root')
  if (!root || !root.children) return []
  return buildMenu(root.children, '')
}
