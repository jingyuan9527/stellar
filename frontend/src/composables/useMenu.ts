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

/**
 * 判断路由是否对游客公开：天然公开（meta.requiresAuth===false）或后端配置公开（publicKeys 命中）。
 */
function isRoutePublic(r: RouteRecordRaw, fullPath: string, publicKeys: string[]): boolean {
  if (r.meta?.requiresAuth === false) return true
  return publicKeys.includes(fullPath)
}

/**
 * 由静态路由构建侧边菜单。
 * @param isLogin 登录态：未登录时仅展示公开路由（天然公开 + 后端配置 publicKeys）；
 *                登录后展示全部。子项被过滤空的父分组自动隐藏。
 */
function buildMenu(routeRecords: RouteRecordRaw[], parentPath: string, isLogin: boolean, publicKeys: string[] = []): MenuOption[] {
  return routeRecords
    .filter((r) => !r.meta?.hidden)
    .filter((r) => {
      if (isLogin) return true
      // 父菜单：保留，交由递归处理子项，子项过滤后为空则父会被末尾 filter 去掉；
      // 叶子：按公开性判断（天然公开或 publicKeys 命中）。
      const visibleChildren = r.children?.filter((c) => !c.meta?.hidden) ?? []
      if (visibleChildren.length > 0) return true
      return isRoutePublic(r, joinPath(parentPath, r.path), publicKeys)
    })
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
        option.children = buildMenu(r.children, fullPath, isLogin, publicKeys)
      }
      return option
    })
    .filter((o) => !o.children || o.children.length > 0)
}

export function generateMenus(isLogin = true, publicKeys: string[] = []): Array<MenuOption | MenuGroupOption> {
  const root = routes.find((r) => r.name === 'Root')
  if (!root || !root.children) return []
  return buildMenu(root.children, '', isLogin, publicKeys)
}
