/**
 * 数据海报生成：纯本地 Canvas 渲染并导出 PNG。
 * 不加载任何远程图片（避免跨域画布污染导致导出失败），头像以首字母圆形代替。
 */

export interface ShareCardMetric {
  label: string
  value: string
}

export interface ShareCardOptions {
  /** 主标题（选手名/战队名） */
  title: string
  /** 副标题（战队 · 赛段范围） */
  subtitle: string
  /** 首字母头像内容（通常传名称首字符） */
  badge: string
  /** 指标列表，最多展示 8 项 */
  metrics: ShareCardMetric[]
  /** 可选：八维雷达分值（0-100），绘制在右侧 */
  radarScores?: number[] | null
  radarLabels?: string[]
  /** 页脚品牌文案 */
  footer?: string
}

const WIDTH = 960
const HEIGHT = 520
const ACCENT = '#2f855a'
const TEXT = '#24292f'
const MUTED = '#57606a'
const LINE = '#d8dee4'

export async function renderShareCard(options: ShareCardOptions): Promise<Blob> {
  const canvas = document.createElement('canvas')
  canvas.width = WIDTH
  canvas.height = HEIGHT
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('当前浏览器不支持 Canvas')

  // 背景
  const gradient = ctx.createLinearGradient(0, 0, WIDTH, HEIGHT)
  gradient.addColorStop(0, '#ffffff')
  gradient.addColorStop(1, '#eef4f0')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, WIDTH, HEIGHT)
  ctx.fillStyle = ACCENT
  ctx.fillRect(0, 0, WIDTH, 8)

  // 首字母徽章
  ctx.beginPath()
  ctx.arc(88, 96, 40, 0, Math.PI * 2)
  ctx.fillStyle = ACCENT
  ctx.fill()
  ctx.fillStyle = '#ffffff'
  ctx.font = '700 34px "Segoe UI", "Microsoft YaHei", sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(options.badge.slice(0, 1) || '·', 88, 98)

  // 标题与副标题
  ctx.textAlign = 'left'
  ctx.fillStyle = TEXT
  ctx.font = '800 38px "Segoe UI", "Microsoft YaHei", sans-serif'
  ctx.fillText(options.title, 150, 84)
  ctx.fillStyle = MUTED
  ctx.font = '500 20px "Segoe UI", "Microsoft YaHei", sans-serif'
  ctx.fillText(options.subtitle, 150, 122)

  // 指标网格（左半区，两列）
  const metrics = options.metrics.slice(0, 8)
  const hasRadar = !!options.radarScores && options.radarScores.length >= 3
  const gridWidth = hasRadar ? 470 : 780
  const columns = 2
  const rows = Math.ceil(metrics.length / columns)
  const cellWidth = gridWidth / columns
  const cellHeight = 74
  metrics.forEach((metric, index) => {
    const column = index % columns
    const row = Math.floor(index / columns)
    const x = 60 + column * cellWidth
    const y = 180 + row * cellHeight
    ctx.fillStyle = MUTED
    ctx.font = '600 16px "Segoe UI", "Microsoft YaHei", sans-serif'
    ctx.fillText(metric.label, x, y)
    ctx.fillStyle = ACCENT
    ctx.font = '800 28px "Segoe UI", "Microsoft YaHei", sans-serif'
    ctx.fillText(metric.value, x, y + 34)
    ctx.strokeStyle = LINE
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(x, y + 48)
    ctx.lineTo(x + cellWidth - 30, y + 48)
    ctx.stroke()
  })

  // 雷达（右半区）
  if (hasRadar && options.radarScores && options.radarLabels) {
    drawRadar(ctx, options.radarScores, options.radarLabels, 700, 340, 140)
  }

  // 页脚
  ctx.fillStyle = MUTED
  ctx.font = '500 16px "Segoe UI", "Microsoft YaHei", sans-serif'
  ctx.fillText(options.footer ?? 'LoL Data Hub · 数据源于赛事官网采集', 60, HEIGHT - 40)
  ctx.fillStyle = LINE
  ctx.fillRect(60, HEIGHT - 66, WIDTH - 120, 1)

  return await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob)
      else reject(new Error('海报导出失败'))
    }, 'image/png')
  })
}

function drawRadar(
  ctx: CanvasRenderingContext2D,
  scores: number[],
  labels: string[],
  centerX: number,
  centerY: number,
  radius: number,
) {
  const axes = scores.length
  const angleAt = (index: number) => (Math.PI * 2 * index) / axes - Math.PI / 2
  const pointAt = (index: number, ratio: number) => ({
    x: centerX + radius * ratio * Math.cos(angleAt(index)),
    y: centerY + radius * ratio * Math.sin(angleAt(index)),
  })

  ctx.strokeStyle = LINE
  ctx.lineWidth = 1
  for (const level of [0.33, 0.66, 1]) {
    ctx.beginPath()
    for (let index = 0; index <= axes; index += 1) {
      const point = pointAt(index % axes, level)
      if (index === 0) ctx.moveTo(point.x, point.y)
      else ctx.lineTo(point.x, point.y)
    }
    ctx.stroke()
  }

  ctx.fillStyle = 'rgba(47, 133, 90, .18)'
  ctx.strokeStyle = ACCENT
  ctx.lineWidth = 2.4
  ctx.beginPath()
  for (let index = 0; index <= axes; index += 1) {
    const point = pointAt(index % axes, Math.min(Math.max(scores[index % axes], 0), 100) / 100)
    if (index === 0) ctx.moveTo(point.x, point.y)
    else ctx.lineTo(point.x, point.y)
  }
  ctx.closePath()
  ctx.fill()
  ctx.stroke()

  ctx.fillStyle = MUTED
  ctx.font = '600 13px "Segoe UI", "Microsoft YaHei", sans-serif'
  ctx.textAlign = 'center'
  labels.forEach((label, index) => {
    const point = pointAt(index, 1.18)
    ctx.fillText(label, point.x, point.y)
  })
}

/** 触发浏览器下载海报文件。 */
export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  globalThis.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
