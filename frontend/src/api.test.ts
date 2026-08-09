import { afterEach, describe, expect, it, vi } from 'vitest'

import { api } from './api'

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('API 客户端', () => {
  it('解析正常 JSON 响应', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: true,
      data: [{ sourceSeasonId: 237, name: '2026LPL' }],
      message: null,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    await expect(api.seasons()).resolves.toEqual([{ sourceSeasonId: 237, name: '2026LPL' }])
  })

  it('将非 JSON 限流响应转换为可理解的 HTTP 错误', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>limited</html>', {
      status: 503,
      headers: { 'Content-Type': 'text/html' },
    })))

    await expect(api.seasons()).rejects.toThrow('请求失败：HTTP 503')
  })

  it('拒绝 HTTP 200 下的非 JSON 异常响应', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('not-json', { status: 200 })))

    await expect(api.seasons()).rejects.toThrow('服务返回了无法识别的响应')
  })

  it('在请求超时时主动中止并返回明确错误', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('fetch', vi.fn().mockImplementation((_input: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => {
          reject(new DOMException('aborted', 'AbortError'))
        })
      }),
    ))

    const assertion = expect(api.seasons()).rejects.toThrow('请求超时，请稍后重试')
    await vi.advanceTimersByTimeAsync(12_001)
    await assertion
  })
})
