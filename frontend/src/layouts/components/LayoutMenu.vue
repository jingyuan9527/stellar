<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuOption } from 'naive-ui'
import { generateMenus } from '@/composables/useMenu'
import { useAuthStore } from '@/store/auth'
import { useMenuStore } from '@/store/menu'

const props = withDefaults(defineProps<{ collapsed?: boolean }>(), {
  collapsed: false,
})

const emit = defineEmits<{ select: [key: string] }>()

/** 侧栏菜单主题：统一到首页设计语言。hover=浅底、active=品牌色（底色/文字由 CSS 补充品牌浅底+左侧竖条）。 */
const menuThemeOverrides = {
  borderRadius: 'var(--r-md)',
  itemColorHover: 'var(--c-fill-2)',
  itemColorActive: 'transparent',
  itemColorActiveHover: 'var(--c-brand-bg)',
  itemColorActiveCollapsed: 'var(--c-brand-bg)',
  itemTextColorActive: 'var(--c-brand)',
  itemTextColorActiveHover: 'var(--c-brand)',
  itemTextColorChildActive: 'var(--c-brand)',
  itemTextColorChildActiveHover: 'var(--c-brand)',
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const menuStore = useMenuStore()

const menuOptions = computed<MenuOption[]>(() =>
  generateMenus(authStore.isLogin, menuStore.publicKeys) as MenuOption[],
)

onMounted(() => {
  if (!authStore.isLogin) menuStore.loadPublicConfig()
})
const activeKey = computed(() => route.path)
const openKeys = ref<string[]>([])

function syncOpenKeys() {
  openKeys.value = route.matched
    .filter((m) => m.path !== '/' && m.path !== route.path)
    .map((m) => m.path)
}

watch(() => route.path, syncOpenKeys, { immediate: true })

function handleSelect(key: string) {
  router.push(key)
  emit('select', key)
}
</script>

<template>
  <NMenu
    :collapsed="props.collapsed"
    :collapsed-width="64"
    :collapsed-icon-size="20"
    :options="menuOptions"
    :value="activeKey"
    :expanded-keys="openKeys"
    :indent="18"
    :theme-overrides="menuThemeOverrides"
    accordion
    @update:value="handleSelect"
    @update:expanded-keys="(keys: string[]) => (openKeys = keys)"
  />
</template>

<style scoped>
/* Naive NMenu 默认 ::before 即带 8px 左右内缩（与侧栏边缘留 8px）+ --n-border-radius 圆角，
   圆角统一到 --r-md，故此处只补品牌色竖条与选中底，不再重复造底。 */

/* active 态：品牌浅底 + 左侧 3px 品牌色竖条 */
:deep(.n-menu-item-content--selected::before) {
  background: var(--c-brand-bg);
  box-shadow: inset 3px 0 0 var(--c-brand);
}

/* 分组标题（子菜单项）：沿用首页 group-title 语言 —— 12px/600 品牌色 + 左侧 3px 品牌色竖条 */
:deep(.n-submenu > .n-menu-item-content .n-menu-item-content-header) {
  font-size: 12px;
  font-weight: 600;
  color: var(--c-brand);
}

:deep(.n-submenu > .n-menu-item-content::before) {
  box-shadow: inset 3px 0 0 var(--c-brand);
}

/* 折叠态分组标题只剩图标，去掉竖条避免突兀 */
:deep(.n-menu--collapsed .n-submenu > .n-menu-item-content::before) {
  box-shadow: none;
}
</style>
