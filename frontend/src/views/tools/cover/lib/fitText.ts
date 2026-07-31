export interface FitOptions {
  min: number
  max: number
  lineHeight: number
  maxWidth: number
  maxHeight: number
  weight: number
  letterSpacing: string
}

export function fitTextToBox(el: HTMLElement, text: string, opts: FitOptions): void {
  const { min, max, lineHeight, maxWidth, maxHeight, weight, letterSpacing } = opts

  el.textContent = text
  el.style.fontWeight = String(weight)
  el.style.letterSpacing = letterSpacing
  el.style.lineHeight = String(lineHeight)

  let low = min
  let high = max
  let best = min

  while (low <= high) {
    const mid = Math.floor((low + high) / 2)
    el.style.fontSize = `${mid}px`
    el.style.maxWidth = `${maxWidth}px`

    if (el.scrollWidth <= maxWidth + 1 && el.scrollHeight <= maxHeight + 1) {
      best = mid
      low = mid + 1
    } else {
      high = mid - 1
    }
  }

  el.style.fontSize = `${best}px`
  el.style.maxWidth = `${maxWidth}px`
}
