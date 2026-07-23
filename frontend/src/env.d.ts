/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

declare global {
  interface Window {
    $message: import('naive-ui').MessageApiInjection
    $dialog: import('naive-ui').DialogApiInjection
    $notification: import('naive-ui').NotificationApiInjection
    $loadingBar: import('naive-ui').LoadingBarApiInjection
  }
}

export {}
