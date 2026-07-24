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
    accordion
    @update:value="handleSelect"
    @update:expanded-keys="(keys: string[]) => (openKeys = keys)"
  />
</template>
