import { describe, it, expect } from 'vitest'
import { buildAgentInstruction } from './agent-instruction'
describe('Agent self enrollment instructions', () => {
  it('contains a one-time exchange rather than a long-lived credential or installation script', () => {
    const text = buildAgentInstruction('https://blog.example', 'x'.repeat(24))
    const body = JSON.parse(text.match(/请求体：(\{[^\n]+\})/)![1])
    expect(body.code).toBe('x'.repeat(24))
    expect(text).toContain('https://blog.example/apis/api.space.onoe.cn/v1alpha1/mcpenrollments')
    expect(text).toContain('X-Frame-MCP-Token')
    expect(text).toContain('tools/list')
    expect(text).toContain('site_info')
    expect(text).toContain('需要重启才能生效')
    expect(text).not.toContain('curl |')
    expect(text).not.toContain('YOUR_MCP_TOKEN')
  })
  it('rejects malformed enrollment data', () => {
    expect(() => buildAgentInstruction('http://blog.example', 'x'.repeat(24))).toThrow()
    expect(() => buildAgentInstruction('https://blog.example/path', 'x'.repeat(24))).toThrow()
    expect(() => buildAgentInstruction('https://blog.example', 'bad')).toThrow()
  })
})
