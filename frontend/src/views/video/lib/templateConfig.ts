import type { CSSProperties } from 'vue'
import type { Ratio, TemplateId } from '../types'

export interface TemplateConfig {
  frameStyle: CSSProperties
  stackStyle: CSSProperties
  badgeStyle: CSSProperties
  titleMaxWidth: number
  subtitleMaxWidth: number
  titleRange: [number, number]
  subtitleRange: [number, number]
  titleLineHeight: number
  subtitleLineHeight: number
  subtitleMarginTop: number
  titleLetterSpacing: string
}

export function getTemplateConfig(templateId: TemplateId, ratio: Ratio): TemplateConfig {
  const portrait = ratio === 'portrait'

  if (templateId === 'center') {
    return {
      frameStyle: {
        position: 'relative',
        zIndex: 10,
        margin: '0 auto',
        display: 'flex',
        height: '100%',
        width: '100%',
        maxWidth: '100%',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '32px 24px',
        textAlign: 'center',
      },
      stackStyle: {
        display: 'flex',
        width: '100%',
        maxWidth: portrait ? '84%' : '82%',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        textAlign: 'center',
      },
      badgeStyle: {
        marginBottom: '12px',
        display: 'inline-flex',
        borderRadius: '9999px',
        border: '1px solid rgba(255,255,255,0.2)',
        background: 'rgba(255,255,255,0.1)',
        padding: '4px 16px',
        fontSize: '12px',
        fontWeight: 500,
        letterSpacing: '0.28em',
        color: 'rgba(255,255,255,0.8)',
        textTransform: 'uppercase',
      },
      titleMaxWidth: portrait ? 0.84 : 0.82,
      subtitleMaxWidth: portrait ? 0.8 : 0.75,
      titleRange: portrait ? [34, 68] : [38, 78],
      subtitleRange: portrait ? [14, 28] : [16, 30],
      titleLineHeight: 1.14,
      subtitleLineHeight: 1.52,
      subtitleMarginTop: portrait ? 20 : 18,
      titleLetterSpacing: '-0.03em',
    }
  }

  if (templateId === 'top-left') {
    return {
      frameStyle: {
        position: 'relative',
        zIndex: 10,
        display: 'flex',
        height: '100%',
        width: '100%',
        flexDirection: 'row',
        alignItems: 'flex-start',
        justifyContent: 'flex-start',
        padding: portrait ? '40px 32px' : '40px 40px',
        textAlign: 'left',
      },
      stackStyle: {
        display: 'flex',
        width: '100%',
        maxWidth: portrait ? '88%' : '72%',
        flexDirection: 'column',
        alignItems: 'flex-start',
        justifyContent: 'flex-start',
        textAlign: 'left',
      },
      badgeStyle: {
        marginBottom: '16px',
        display: 'inline-flex',
        borderRadius: '9999px',
        border: '1px solid rgba(255,255,255,0.2)',
        background: 'rgba(0,0,0,0.2)',
        padding: '4px 16px',
        fontSize: '12px',
        fontWeight: 600,
        letterSpacing: '0.24em',
        color: 'rgba(255,255,255,0.85)',
        textTransform: 'uppercase',
      },
      titleMaxWidth: portrait ? 0.88 : 0.72,
      subtitleMaxWidth: portrait ? 0.84 : 0.66,
      titleRange: portrait ? [30, 62] : [34, 66],
      subtitleRange: portrait ? [14, 24] : [15, 24],
      titleLineHeight: 1.12,
      subtitleLineHeight: 1.48,
      subtitleMarginTop: portrait ? 18 : 16,
      titleLetterSpacing: '-0.035em',
    }
  }

  // bottom-impact
  return {
    frameStyle: {
      position: 'relative',
      zIndex: 10,
      display: 'flex',
      height: '100%',
      width: '100%',
      flexDirection: 'row',
      alignItems: 'flex-end',
      justifyContent: portrait ? 'center' : 'flex-start',
      padding: portrait ? '64px 28px 40px' : '64px 40px 32px',
      textAlign: 'left',
    },
    stackStyle: {
      display: 'flex',
      width: '100%',
      maxWidth: portrait ? '86%' : '78%',
      flexDirection: 'column',
      alignItems: portrait ? 'center' : 'flex-start',
      justifyContent: 'flex-end',
      textAlign: portrait ? 'center' : 'left',
    },
    badgeStyle: {
      marginBottom: '16px',
      display: 'inline-flex',
      borderRadius: '9999px',
      border: '1px solid rgba(255,255,255,0.15)',
      background: 'rgba(0,0,0,0.3)',
      padding: '4px 16px',
      fontSize: '11px',
      fontWeight: 600,
      letterSpacing: '0.28em',
      color: 'rgba(255,255,255,0.85)',
      textTransform: 'uppercase',
    },
    titleMaxWidth: portrait ? 0.86 : 0.78,
    subtitleMaxWidth: portrait ? 0.8 : 0.62,
    titleRange: portrait ? [36, 72] : [42, 86],
    subtitleRange: portrait ? [14, 24] : [16, 24],
    titleLineHeight: 1.08,
    subtitleLineHeight: 1.42,
    subtitleMarginTop: portrait ? 20 : 18,
    titleLetterSpacing: '-0.05em',
  }
}
