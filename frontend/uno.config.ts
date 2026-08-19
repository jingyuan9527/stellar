import { defineConfig, presetUno, presetAttributify, presetIcons } from 'unocss'

export default defineConfig({
  presets: [
    presetUno(),
    presetAttributify(),
    presetIcons({
      scale: 1.2,
      warn: true,
    }),
  ],
  theme: {
    colors: {
      primary: 'var(--c-brand)',
      info: 'var(--c-info)',
      'text-1': 'var(--c-text-1)',
      'text-2': 'var(--c-text-2)',
      'text-3': 'var(--c-text-3)',
      bg: 'var(--c-bg)',
      fill: 'var(--c-fill)',
      'fill-2': 'var(--c-fill-2)',
      border: 'var(--c-border)',
    },
  },
  shortcuts: {
    'flex-center': 'flex items-center justify-center',
    'flex-between': 'flex items-center justify-between',
    'wh-full': 'w-full h-full',
  },
})
