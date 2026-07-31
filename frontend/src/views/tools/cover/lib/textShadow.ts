export function normalizeHexColor(value: string, fallback: string): string {
  const trimmed = value.trim()
  const fullHex = /^#([0-9a-fA-F]{6})$/
  const shortHex = /^#([0-9a-fA-F]{3})$/

  if (fullHex.test(trimmed)) return trimmed
  if (shortHex.test(trimmed)) {
    return `#${trimmed[1]}${trimmed[1]}${trimmed[2]}${trimmed[2]}${trimmed[3]}${trimmed[3]}`
  }

  return fallback
}

export function combineLetterSpacing(baseSpacing: string, offsetSpacing: string): string {
  return `${(parseFloat(baseSpacing) || 0) + (parseFloat(offsetSpacing) || 0)}em`
}

export function buildTextShadow(
  color: string,
  strokeWidth: number,
  shadowStrength: number,
  glowStrength: number,
): string {
  const shadows: string[] = []
  const strokeColor = normalizeHexColor(color, '#000000')

  if (strokeWidth > 0) {
    const step = Math.max(1, Math.round(strokeWidth))
    for (let x = -step; x <= step; x += 1) {
      for (let y = -step; y <= step; y += 1) {
        if (x === 0 && y === 0) continue
        shadows.push(`${x}px ${y}px 0 ${strokeColor}`)
      }
    }
  }

  if (shadowStrength > 0) {
    const blur = Math.round(shadowStrength * 0.55)
    const alpha = Math.min(0.65, shadowStrength / 130)
    shadows.push(`0 ${Math.max(4, Math.round(shadowStrength / 10))}px ${blur}px rgba(0,0,0,${alpha})`)
  }

  if (glowStrength > 0) {
    const glowBlur = Math.round(glowStrength * 0.4)
    const glowAlpha = Math.min(0.9, glowStrength / 110)
    shadows.push(`0 0 ${glowBlur}px rgba(255,255,255,${glowAlpha})`)
  }

  return shadows.join(', ')
}
