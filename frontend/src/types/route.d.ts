import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    icon?: string
    order?: number
    hidden?: boolean
    affix?: boolean
    closable?: boolean
    requiresAuth?: boolean
  }
}

export interface TabItem {
  path: string
  name: string
  title: string
  icon?: string
  closable: boolean
}
