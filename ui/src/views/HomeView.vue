<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { axiosInstance } from '@halo-dev/api-client'
import { buildAgentInstruction } from '../utils/agent-instruction'
import IconBook from '~icons/solar/notebook-linear'
import IconCalendar from '~icons/solar/calendar-mark-linear'
import IconLock from '~icons/solar/lock-keyhole-linear'
import IconPhone from '~icons/solar/smartphone-linear'
import IconPlug from '~icons/solar/plug-circle-linear'
interface RecordItem { metadata: { name: string; creationTimestamp?: string }; spec: { content: string; mood?: string } }
interface DailyItem { id: string; text: string; done: boolean; date: string; url: string }
interface DailyState { apiVersion: string; kind: string; metadata: { name: string; version?: number }; items: DailyItem[] }
const tab = ref('overview'), records = ref<RecordItem[]>([]), daily = ref<DailyState | null>(null)
const content = ref(''), mood = ref(''), busy = ref(false), loading = ref(false), error = ref(''), message = ref(''), bindingCode = ref('')
const ownerBound = ref<boolean | null>(null)
async function loadBinding(){try{const {data}=await axiosInstance.get('/apis/console.api.space.onoe.cn/v1alpha1/bindingstatus');ownerBound.value=data.bound;bindingCode.value=''}catch(e){feedback(e,'绑定状态读取失败，请刷新重试。')}}
const kind = ref('todo'), draft = ref(''), date = ref(''), url = ref('')
const kinds = [{ id: 'todo', title: '待办事项' }, { id: 'anniversary', title: '纪念日' }, { id: 'link', title: '常用链接' }]
const tabs = [{ id: 'overview', title: '空间概览', icon: IconBook }, { id: 'records', title: '私密日记', icon: IconLock }, { id: 'daily', title: '日常云端', icon: IconCalendar }, { id: 'binding', title: '微信绑定', icon: IconPhone }, { id: 'mcp', title: 'MCP 接入', icon: IconPlug }]
const recordEndpoint = '/apis/space.onoe.cn/v1alpha1/liferecords'
const dailyEndpoint = '/apis/space.onoe.cn/v1alpha1/dailystates'
const selectedKind = computed(() => kinds.find(item => item.id === kind.value)?.title)
function feedback(cause: unknown, fallback: string) { const status = (cause as { response?: { status?: number } })?.response?.status; error.value = status === 409 ? '云端已有更新，请重新读取后再修改。' : fallback }
async function loadRecords() {
  loading.value = true; error.value = ''
  try { const { data } = await axiosInstance.get(recordEndpoint, { params: { page: 1, size: 100, sort: 'metadata.creationTimestamp,desc' } }); records.value = data.items }
  catch (e) { feedback(e, '日记读取失败，请检查连接后重试。') } finally { loading.value = false }
}
async function saveRecord() {
  if (busy.value || !content.value.trim()) return
  busy.value = true; error.value = ''; message.value = ''
  try { await axiosInstance.post(recordEndpoint, { apiVersion: 'space.onoe.cn/v1alpha1', kind: 'LifeRecord', metadata: { name: `record-${crypto.randomUUID()}` }, spec: { content: content.value.trim(), mood: mood.value.trim() } }); content.value = ''; mood.value = ''; await loadRecords(); message.value = '私密日记已保存，小程序刷新后可见。' }
  catch (e) { feedback(e, '保存失败，内容已保留，请重试。') } finally { busy.value = false }
}
async function issueBinding() {
  if (busy.value || ownerBound.value !== false) return
  busy.value = true; error.value = ''; bindingCode.value = ''
  try { const { data } = await axiosInstance.post('/apis/console.api.space.onoe.cn/v1alpha1/bindingcodes'); bindingCode.value = data.code }
  catch (e) { feedback(e, '绑定码生成失败，请确认管理员权限后重试。') } finally { busy.value = false }
}
async function loadDaily() {
  loading.value = true; error.value = ''; message.value = ''; daily.value = null
  try { const { data } = await axiosInstance.get(dailyEndpoint, { params: { page: 1, size: 10 } }); daily.value = (data.items || []).find((item: DailyState) => item.metadata.name === kind.value) || null }
  catch (e) { if ((e as { response?: { status?: number } }).response?.status !== 404) feedback(e, '日常数据读取失败，请重试。') }
  finally { loading.value = false }
}
async function chooseKind(value: string) { if (busy.value || loading.value) return; kind.value = value; draft.value = ''; date.value = ''; url.value = ''; await loadDaily() }
async function persistDaily(items: DailyItem[]) {
  if (busy.value || loading.value) return
  busy.value = true; error.value = ''; message.value = ''
  const state: DailyState = daily.value ? { ...daily.value, items } : { apiVersion: 'space.onoe.cn/v1alpha1', kind: 'DailyState', metadata: { name: kind.value }, items }
  try { const { data } = daily.value ? await axiosInstance.put(`${dailyEndpoint}/${kind.value}`, state) : await axiosInstance.post(dailyEndpoint, state); daily.value = data; message.value = '已保存到 Halo，小程序下次进入或恢复前台时自动同步。'; return true }
  catch (e) { feedback(e, '保存失败，原有云端数据未被替换，请重新读取后重试。'); return false }
  finally { busy.value = false }
}
async function addDaily() {
  if (!draft.value.trim()) return
  if ((daily.value?.items?.length || 0) >= 200) { error.value = '每组最多保存 200 项。'; return }
  if (kind.value === 'anniversary' && !date.value) { error.value = '请选择日期。'; return }
  if (kind.value === 'link' && !/^https?:\/\/[^\s]+$/i.test(url.value.trim())) { error.value = '请输入完整的 HTTP 或 HTTPS 链接。'; return }
  if (await persistDaily([...(daily.value?.items || []), { id: String(Date.now()), text: draft.value.trim(), done: false, date: date.value, url: url.value.trim() }])) { draft.value = ''; date.value = ''; url.value = '' }
}
async function toggleDaily(id: string) { await persistDaily((daily.value?.items || []).map(item => item.id === id ? { ...item, done: !item.done } : item)) }
async function navigate(id: string) { if (busy.value || loading.value) return; tab.value = id; error.value = ''; message.value = ''; if (id === 'binding') await loadBinding(); if (id === 'daily') await loadDaily(); if (id === 'frame') await loadFrame(); if (id === 'mcp') await loadMcp() }
function formatTime(value?: string) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '' }

interface FrameProfile { apiVersion: string; kind: string; metadata: { name: string; version?: number }; enabled: boolean; siteName: string; author: string; description: string; tagline: string; avatar: string }
const profilePath = '/apis/space.onoe.cn/v1alpha1/frameprofiles/site'
const frameProfile = ref<FrameProfile>({ apiVersion: 'space.onoe.cn/v1alpha1', kind: 'FrameProfile', metadata: { name: 'site' }, enabled: false, siteName: '', author: '', description: '', tagline: '', avatar: '' })
const frameExists = ref(false), frameSiteId = ref(''), frameQrFailed = ref(false)
const frameGateway = 'https://onoe.cn/frame-api'
async function loadFrame() {
  loading.value = true
  try { const { data } = await axiosInstance.get(profilePath); frameProfile.value = data; frameExists.value = true; if (data.enabled) await restoreFrameEntry() }
  catch (e) { if ((e as { response?: { status?: number } }).response?.status !== 404) feedback(e, '站点配置读取失败。') }
  finally { loading.value = false }
}
async function restoreFrameEntry() {
  try {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(window.location.origin))
    const id = Array.from(new Uint8Array(digest)).map(n => n.toString(16).padStart(2, '0')).join('').slice(0, 24)
    const response = await fetch(frameGateway + '/v1/sites/' + id, { credentials: 'omit' })
    if (response.ok) frameSiteId.value = id
  } catch { /* The connect button remains available when the gateway is offline. */ }
}
async function saveFrame() {
  busy.value = true; error.value = ''; message.value = ''
  try {
    const { data } = frameExists.value ? await axiosInstance.put(profilePath, frameProfile.value) : await axiosInstance.post('/apis/space.onoe.cn/v1alpha1/frameprofiles', frameProfile.value)
    frameProfile.value = data; frameExists.value = true; if (!data.enabled) frameSiteId.value = ''; message.value = data.enabled ? '公开内容接入已启用，点击连接以生成空间入口。' : '已停止公开接入，接入服务将在一分钟内停止新的读取；设备上已加载的内容可能保留。'
    return true
  } catch (e) { feedback(e, '保存失败，请重新读取后重试。'); return false }
  finally { busy.value = false }
}
async function connectFrame() {
  if (busy.value || loading.value || !frameProfile.value.enabled) return
  if (!await saveFrame()) return
  busy.value = true; error.value = ''; frameQrFailed.value = false
  try {
    const { data: proof } = await axiosInstance.post('/apis/console.api.space.onoe.cn/v1alpha1/framecodes')
    const response = await fetch(frameGateway + '/v1/sites', { method: 'POST', credentials: 'omit', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ siteUrl: window.location.origin, code: proof.code }) })
    const data = await response.json()
    if (!response.ok) throw new Error(data.error || '连接失败')
    frameSiteId.value = data.id; message.value = '博客已连接小染 Frame。扫码进入，或在小程序“博客空间”中输入空间编号。'
  } catch (e) { error.value = e instanceof Error ? e.message : '连接失败，请稍后重试。' }
  finally { busy.value = false }
}


interface McpConfig { enabled: boolean; allowedOrigin: string; hasMcpToken: boolean }
const mcpConfig = ref<McpConfig>({ enabled: false, allowedOrigin: '', hasMcpToken: false })
const mcpToken = ref(''), mcpCode = ref(''), agentInstruction = ref('')
const instructionVisible = ref(false)
const agentConnections = ref<Array<{ id: string; name: string; createdAt: string; writeEnabled: boolean }>>([])
const mcpUrl = window.location.origin + '/apis/api.space.onoe.cn/v1alpha1/mcp'
const agentConfig = computed(() => JSON.stringify({ mcpServers: { 'xiaoran-frame': { type: 'http', url: mcpUrl, headers: { 'X-Frame-MCP-Token': (mcpToken.value || 'YOUR_MCP_TOKEN') } } } }, null, 2))
async function loadMcp() {
  loading.value = true; mcpToken.value = ''; mcpCode.value = ''; agentInstruction.value = ''; instructionVisible.value = false
  try { const { data } = await axiosInstance.get('/apis/console.api.space.onoe.cn/v1alpha1/mcpconfig/current'); mcpConfig.value = data; if (!data.allowedOrigin) mcpConfig.value.allowedOrigin = window.location.origin; const agents = await axiosInstance.get('/apis/console.api.space.onoe.cn/v1alpha1/mcpagents'); agentConnections.value = agents.data }
  catch (e) { feedback(e, 'MCP 配置读取失败。') } finally { loading.value = false }
}


async function toggleAgentWrite(item: { id: string; writeEnabled: boolean }, event: Event) {
  const control = event.target as HTMLInputElement, enabled = control.checked
  control.checked = item.writeEnabled
  if (enabled && !window.confirm('允许这个 Agent 上传图片和发布瞬间？图片沿用本站 Halo 存储；Agent 会在对话中先向你确认再发布。')) return
  busy.value = true; error.value = ''
  try {
    await axiosInstance.put('/apis/console.api.space.onoe.cn/v1alpha1/mcpagents/' + encodeURIComponent(item.id) + '/permissions', { writeEnabled: enabled })
    item.writeEnabled = enabled; control.checked = enabled
    message.value = enabled ? '已授权。让 Agent 刷新工具列表，即可上传图片并在对话确认后直接发布。' : '已收回写入权限，此 Agent 继续只读。'
  } catch (e) { feedback(e, '授权失败，请确认 Halo 已有默认附件存储。') } finally { busy.value = false }
}

async function copyAgentInstruction() {
  if (busy.value || loading.value) return
  busy.value = true; error.value = ''; message.value = ''
  try {
    {
      if (!mcpConfig.value.enabled) {
        const { data } = await axiosInstance.put('/apis/console.api.space.onoe.cn/v1alpha1/mcpconfig/current', { enabled: true, allowedOrigin: mcpConfig.value.allowedOrigin || window.location.origin })
        mcpConfig.value = data
      }
      const { data } = await axiosInstance.post('/apis/console.api.space.onoe.cn/v1alpha1/mcpagentcodes')
      agentInstruction.value = buildAgentInstruction(window.location.origin, data.code)
    }
    try { await navigator.clipboard.writeText(agentInstruction.value); instructionVisible.value = false; message.value = '接入指令已复制，整段发给你的 Agent 即可。五分钟内有效。' }
    catch { instructionVisible.value = true; message.value = '接入指令已生成，可选中下方整段文字复制给 Agent。五分钟内有效。' }
  } catch (e) { feedback(e, '接入指令生成失败，请刷新连接列表后重试。') }
  finally { busy.value = false }
}
async function refreshAgents() {
  loading.value = true; error.value = ''
  try { const { data } = await axiosInstance.get('/apis/console.api.space.onoe.cn/v1alpha1/mcpagents'); agentConnections.value = data }
  catch (e) { feedback(e, '连接列表读取失败。') } finally { loading.value = false }
}
async function disconnectAgent(id: string) {
  if (!window.confirm('断开后，这个 Agent 将无法继续读取博客。继续吗？')) return
  busy.value = true; error.value = ''
  try { await axiosInstance.delete('/apis/console.api.space.onoe.cn/v1alpha1/mcpagents/' + encodeURIComponent(id)); agentConnections.value = agentConnections.value.filter(item => item.id !== id); message.value = '已断开这个 Agent。其他连接继续可用。' }
  catch (e) { feedback(e, '断开失败，请重试。') } finally { busy.value = false }
}

async function saveMcp() {
  busy.value = true; error.value = ''; message.value = ''
  try { const { data } = await axiosInstance.put('/apis/console.api.space.onoe.cn/v1alpha1/mcpconfig/current', { enabled: mcpConfig.value.enabled, allowedOrigin: mcpConfig.value.allowedOrigin }); mcpConfig.value = data; message.value = data.enabled ? 'MCP 已启用。将服务地址与凭证填入你自己的 Agent。' : 'MCP 已停用，Agent 和小程序不能继续调用。' }
  catch (e) { feedback(e, 'MCP 配置保存失败。') } finally { busy.value = false }
}
async function issueMcpToken() {
  if (busy.value || loading.value) return
  if (mcpConfig.value.hasMcpToken && !window.confirm('生成新凭证将使原 Agent 凭证立即失效，继续吗？')) return
  busy.value = true; error.value = ''; message.value = ''
  try { const { data } = await axiosInstance.post('/apis/console.api.space.onoe.cn/v1alpha1/mcptokens'); mcpToken.value = data.token; mcpConfig.value.hasMcpToken = true; message.value = '凭证只在本次页面显示，请保存到自己的 Agent。服务端只保存凭证摘要。' }
  catch (e) { feedback(e, '凭证生成失败。') } finally { busy.value = false }
}
async function revokeMcpToken() {
  if (!window.confirm('撤销后，使用此凭证的 Agent 将无法访问博客。继续吗？')) return
  busy.value = true; error.value = ''
  try { await axiosInstance.delete('/apis/console.api.space.onoe.cn/v1alpha1/mcptokens/current'); mcpToken.value = ''; mcpConfig.value.hasMcpToken = false; message.value = 'Agent 访问凭证已撤销。' }
  catch (e) { feedback(e, '撤销失败。') } finally { busy.value = false }
}
async function issueMcpCode() {
  busy.value = true; error.value = ''; mcpCode.value = ''
  try { const { data } = await axiosInstance.post('/apis/console.api.space.onoe.cn/v1alpha1/mcpconnectcodes'); mcpCode.value = data.code }
  catch (e) { feedback(e, '连接码生成失败，请先启用 MCP。') } finally { busy.value = false }
}

onMounted(() => { loadRecords(); loadBinding() })
</script>

<template>
  <main class="xr-space">
    <header class="xr-hero"><div><span class="xr-eyebrow">XIAORAN · PERSONAL SPACE</span><h1>小染 Frame</h1><p>让博客与掌心里的生活，连在一起。</p></div><a class="xr-button xr-secondary" href="/" target="_blank" rel="noopener">查看博客</a></header>
    <nav class="xr-tabs" aria-label="空间管理"><button v-for="item in tabs" :key="item.id" :class="{ active: tab === item.id }" :disabled="busy || loading" @click="navigate(item.id)"><component :is="item.icon" />{{ item.title }}</button></nav>
    <p v-if="error" class="xr-alert xr-error" role="alert">{{ error }}</p><p v-if="message" class="xr-alert xr-success" role="status">{{ message }}</p>
    <template v-if="tab === 'mcp'">
      <section class="xr-card xr-mcp-onboarding"><span class="xr-tag">一次复制，即可开始</span><h2>把接入交给你的 Agent</h2><p class="xr-muted">复制一段指令发给它，Agent 会领取自己的连接、保存配置并检查是否连通。</p><div class="xr-actions"><button class="xr-button" :disabled="busy || loading" @click="copyAgentInstruction()">{{ busy ? '准备中…' : '复制接入指令' }}</button></div><details v-if="agentInstruction" :open="instructionVisible" class="xr-agent-instruction"><summary>查看指令内容（可选）</summary><div class="xr-frame-form"><label>整段发给你的 Agent<textarea :value="agentInstruction" readonly rows="12" /></label><p class="xr-muted">五分钟有效，使用一次后失效。过期后重新点“复制接入指令”。Agent 需要有修改自身 MCP 配置的权限。</p></div></details></section>
      <section class="xr-card"><div class="xr-section-head"><h2>已连接的 Agent</h2><button class="xr-button xr-secondary" :disabled="busy || loading" @click="refreshAgents">刷新列表</button></div><p v-if="!agentConnections.length" class="xr-muted">还没有通过接入指令连接的 Agent。接入完成后，在这里查看和断开。</p><div v-for="item in agentConnections" :key="item.id" class="xr-agent-row"><div><strong>{{ item.name }}</strong><p class="xr-muted">{{ formatTime(item.createdAt) }}</p></div><div class="xr-agent-permissions"><label><input type="checkbox" :checked="item.writeEnabled" :disabled="busy || loading" @change="toggleAgentWrite(item, $event)" />允许上传与发布</label><span class="xr-muted">{{item.writeEnabled?'可上传与发布 · 对话确认':'只读'}}</span><button class="xr-button xr-secondary" :disabled="busy || loading" @click="disconnectAgent(item.id)">断开</button></div></div></section>
      <section class="xr-card"><h3>在 Agent 里完成上传与发布</h3><p class="xr-muted">开启一次写入授权后，Agent 可使用本站 Halo 存储上传图片，在对话中向你确认图文后直接发布。</p></section>
      <details class="xr-card xr-mcp-advanced"><summary>高级设置与工具检查</summary><p class="xr-muted">通常使用上方接入指令即可。这里保留手动配置和调试入口。</p>
        <form class="xr-frame-form" @submit.prevent="saveMcp"><label class="xr-frame-toggle"><input v-model="mcpConfig.enabled" type="checkbox" :disabled="busy || loading" />启用博客 MCP 服务</label><label>允许的浏览器来源<input v-model="mcpConfig.allowedOrigin" :disabled="busy || loading" /></label><button class="xr-button xr-secondary" :disabled="busy || loading">保存设置</button></form>
        <div class="xr-frame-form xr-agent-instruction"><label>MCP 服务地址<input :value="mcpUrl" readonly /></label><p class="xr-muted">Streamable HTTP，协议2025-11-25；支持自定义请求头，暂不提供OAuth自动授权。</p><div class="xr-actions"><button class="xr-button xr-secondary" :disabled="busy || loading || !mcpConfig.enabled" @click="issueMcpToken">{{ mcpConfig.hasMcpToken ? '重置手动接入凭证' : '生成手动接入凭证' }}</button><button v-if="mcpConfig.hasMcpToken" class="xr-button xr-secondary" :disabled="busy || loading" @click="revokeMcpToken">撤销手动凭证</button></div><label v-if="mcpToken">手动凭证（仅显示一次）<input :value="mcpToken" readonly /></label><label>手动配置示例<textarea class="xr-mcp-example" :value="agentConfig" rows="11" readonly /></label></div>
        <div class="xr-agent-instruction"><h3>小程序工具检查（可选）</h3><p class="xr-muted">已登录的管理员微信可直接检查。也可使用一次性工具检查码，会话最长十二小时，插件重启后需重连。</p><button class="xr-button xr-secondary" :disabled="busy || loading || !mcpConfig.enabled" @click="issueMcpCode">生成工具检查码</button><div v-if="mcpCode" class="xr-binding-code">{{ mcpCode }}</div></div>
      </details>
    </template>
    <template v-else-if="tab === 'frame'">
      <section class="xr-card xr-frame-card"><div class="xr-section-head"><div><h2>让你的博客，来到掌心</h2><p class="xr-muted">连接小染 Frame，访客看到的都是你自己的博客内容。</p></div><span class="xr-tag">公开内容接入</span></div>
        <form class="xr-frame-form" @submit.prevent="saveFrame">
          <label class="xr-frame-toggle"><input v-model="frameProfile.enabled" type="checkbox" :disabled="busy || loading" />允许小染 Frame 读取本站公开内容</label>
          <label>站点名称<input v-model="frameProfile.siteName" required maxlength="60" :disabled="busy || loading" placeholder="你的博客名称" /></label>
          <label>博主称呼<input v-model="frameProfile.author" required maxlength="40" :disabled="busy || loading" placeholder="访客看到的博主名字" /></label>
          <label>一句话介绍<input v-model="frameProfile.tagline" maxlength="120" :disabled="busy || loading" placeholder="好好生活，慢慢相遇。" /></label>
          <label>站点简介<textarea v-model="frameProfile.description" maxlength="240" :disabled="busy || loading" rows="3" /></label>
          <label>头像地址<input v-model="frameProfile.avatar" maxlength="2048" :disabled="busy || loading" placeholder="https:// 或本站 /upload/ 图片地址" /></label>
          <div class="xr-actions"><button class="xr-button xr-secondary" :disabled="busy || loading" type="submit">保存配置</button><button class="xr-button" :disabled="busy || loading || !frameProfile.enabled || !frameProfile.siteName || !frameProfile.author" type="button" @click="connectFrame">{{ busy ? '连接中…' : '连接小染 Frame' }}</button></div>
        </form>
      </section>
      <section v-if="frameSiteId" class="xr-card xr-frame-result"><h3>你的博客空间入口</h3><img v-if="!frameQrFailed" :src="frameGateway + '/v1/sites/' + frameSiteId + '/qrcode'" alt="微信扫码进入博客空间" @error="frameQrFailed = true" /><p v-else>扫码入口暂未就绪，可先在小程序“我的 → 博客空间”输入编号。</p><label>空间编号<input :value="frameSiteId" readonly aria-label="空间编号" /></label><p class="xr-muted">可公开分享此编号。它只用于访问公开内容，不是管理员登录凭证。</p></section>
      <section class="xr-card"><h3>内容仍在你自己的 Halo</h3><p class="xr-muted">第一版支持文章、分类、归档、搜索、评论、瞬间、图库和友链的公开读取。瞬间、图库和友链需启用对应插件的公共 API。私密日记、发布和管理权限不会因连接而开放。接入服务会中转公开内容及图片。</p></section>
    </template>
    <template v-else-if="tab === 'overview'">
      <section class="xr-welcome xr-card"><div><span class="xr-tag">与 Halo 相连</span><h2>记录小事，也留住重要的日子。</h2><p>小程序“记录”直接连接博客瞬间。私密日记与日常数据保存在自己的 Halo，公开内容和私密内容分开管理。</p><div class="xr-actions"><a class="xr-button" href="/console/moments">管理博客瞬间</a><button class="xr-button xr-secondary" @click="navigate('records')">写一篇私密日记</button></div></div><IconBook class="xr-hero-icon" /></section>
      <section class="xr-grid"><button class="xr-card xr-feature" @click="navigate('records')"><IconLock /><strong>私密日记</strong><span>{{ loading ? '读取中…' : `最近 ${records.length} 篇` }}</span><p>仅管理端和已绑定的微信可读取。</p></button><button class="xr-card xr-feature" @click="navigate('daily')"><IconCalendar /><strong>日常云端</strong><span>待办 / 纪念日 / 链接</span><p>与已绑定微信的小程序自动同步。</p></button><button class="xr-card xr-feature" @click="navigate('binding')"><IconPhone /><strong>微信绑定</strong><span>自己的微信，自己的空间</span><p>{{ ownerBound ? '微信已绑定，打开小程序自动恢复身份。' : '首次绑定一次，日后直接使用。' }}</p></button></section>
      <section class="xr-card"><div class="xr-section-head"><h2>已经连起来的功能</h2><span class="xr-muted">公开内容从博客读取</span></div><div class="xr-connections"><div><strong>文章与分类</strong><span>正文、分类、归档、搜索、评论读取</span><em>已连接</em></div><div><strong>生活瞬间</strong><span>小程序浏览、发布图文，博客同步展示</span><em>已连接</em></div><div><strong>图库与友链</strong><span>读取本站公开的图库与友链</span><em>已连接</em></div><div><strong>收藏与文字草稿</strong><span>绑定微信后自动同步，字号偏好保存在设备</span><em>自动同步</em></div></div></section>
    </template>
    <template v-else-if="tab === 'records'">
      <div class="xr-section-head"><div><h2>只写给自己的日常</h2><p class="xr-muted">这里的文字不会发布到博客瞬间。</p></div><button class="xr-button xr-secondary" :disabled="loading || busy" @click="loadRecords">刷新日记</button></div>
      <div class="xr-record-layout"><form class="xr-card xr-compose" @submit.prevent="saveRecord"><h3>今天，想记下什么？</h3><label for="xr-content">正文</label><textarea id="xr-content" v-model="content" :disabled="busy" maxlength="10000" rows="9" required placeholder="不必写得完整，值得记住就好。" /><div class="xr-word-count">{{ content.length }} / 10000</div><label for="xr-mood">心情 <span>（可选）</span></label><input id="xr-mood" v-model="mood" :disabled="busy" maxlength="100" placeholder="轻松、充实，或者一点点期待" /><button class="xr-button" :disabled="busy || !content.trim()">{{ busy ? '保存中…' : '保存私密日记' }}</button></form><section class="xr-journal"><h3>最近日记 <span class="xr-muted">{{ records.length }}</span></h3><div v-if="loading" class="xr-empty">正在读取日记…</div><div v-else-if="!records.length" class="xr-card xr-empty">还没有日记，从今天的一件小事开始。</div><article v-for="item in records" :key="item.metadata.name" class="xr-card"><time>{{ formatTime(item.metadata.creationTimestamp) }}</time><span v-if="item.spec.mood" class="xr-tag">{{ item.spec.mood }}</span><p class="xr-content">{{ item.spec.content }}</p></article></section></div>
    </template>
    <template v-else-if="tab === 'daily'">
      <div class="xr-section-head"><div><h2>把日子，慢慢理顺</h2><p class="xr-muted">这是 Halo 中的私密云端快照。小程序保留本机数据，需要主动上传或拉取。</p></div><button class="xr-button xr-secondary" :disabled="busy || loading" @click="loadDaily">重新读取</button></div>
      <section class="xr-daily-summary"><div><span>当前分组</span><strong>{{ selectedKind }}</strong></div><div><span>云端条目</span><strong>{{ loading ? '—' : daily?.items?.length || 0 }}<small> 项</small></strong></div><div><span>{{ kind === 'todo' ? '已经完成' : '保存位置' }}</span><strong>{{ kind === 'todo' ? (daily?.items || []).filter(item => item.done).length : 'Halo 私密空间' }}<small v-if="kind==='todo'"> 项</small></strong></div></section>
      <div class="xr-daily-workspace"><section class="xr-card xr-daily-main"><div class="xr-kind-tabs"><button v-for="item in kinds" :key="item.id" :disabled="busy || loading" :class="{ active: kind === item.id }" @click="chooseKind(item.id)">{{ item.title }}</button></div><div class="xr-list-intro"><h3>{{ kind === 'todo' ? '一件件小事，慢慢完成' : kind === 'anniversary' ? '把重要的日子留在这里' : '收藏常用的好去处' }}</h3><p>{{ kind === 'todo' ? '安排想做的事，完成后轻轻勾选。' : kind === 'anniversary' ? '生日、相遇、下一次旅行，值得认真记住。' : '让常用网站和喜欢的内容，有自己的位置。' }}</p></div><form class="xr-daily-form" @submit.prevent="addDaily"><input v-model="draft" :disabled="busy || loading" maxlength="100" :aria-label="`${selectedKind}名称`" :placeholder="`添加${selectedKind}`" required /><input v-if="kind === 'anniversary'" v-model="date" :disabled="busy || loading" aria-label="日期" type="date" required /><input v-if="kind === 'link'" v-model="url" :disabled="busy || loading" aria-label="链接地址" placeholder="https://" maxlength="2000" required /><button class="xr-button" :disabled="busy || loading || !draft.trim()">{{ busy ? '保存中…' : '添加到云端' }}</button></form><div v-if="loading" class="xr-empty">正在读取云端…</div><div v-else-if="!daily?.items?.length" class="xr-empty xr-daily-empty"><component :is="kind === 'anniversary' ? IconBook : kind === 'link' ? IconBook : IconCalendar" /><h3>给{{ selectedKind }}留一个位置</h3><p>在上方添加第一项，或者把小程序里的内容保存到云端。</p></div><div v-for="item in daily?.items || []" :key="item.id" class="xr-daily-row"><input v-if="kind === 'todo'" type="checkbox" :checked="item.done" :disabled="busy || loading" :aria-label="`切换 ${item.text} 完成状态`" @change="toggleDaily(item.id)" /><div><strong :class="{ 'xr-done': kind === 'todo' && item.done }">{{ item.text }}</strong><p v-if="kind === 'anniversary'">{{ item.date }}</p><a v-if="kind === 'link'" :href="/^https?:\/\//i.test(item.url) ? item.url : undefined" target="_blank" rel="noopener">{{ item.url }}</a></div></div></section><aside class="xr-daily-aside"><section class="xr-card"><IconPhone class="xr-aside-icon"/><h3>和掌心里的日常相连</h3><p>这里是 Halo 保存的云端内容，小程序也保留一份本机数据。</p><ol><li>在这里添加或更新内容。</li><li>打开小程序「日常」。</li><li>选择同一分组，展开「同步管理」，点击「从云端拉取」。</li></ol><div class="xr-sync-tip">拉取前会保留本机备份。云端有更新时，请先拉取，再继续修改。</div></section><div class="xr-private-note"><IconLock/>这些日常只属于你，不会出现在公开博客。</div></aside></div>
    </template>
    <template v-else>
      <section v-if="ownerBound === true" class="xr-card xr-binding"><span class="xr-tag">微信已绑定</span><h2>你的微信，已经连接此 Halo。</h2><p>打开小程序会自动校验微信身份，直接查看和管理自己的内容。升级小程序或插件无需重新绑定。</p></section><section v-else class="xr-card xr-binding"><IconPhone class="xr-binding-icon" /><span class="xr-tag">管理员身份关联</span><h2>让自己的微信，连接小染空间。</h2><p>在微信小程序的“记录”页，点击微信登录下方的“首次绑定”，填入这里生成的一次性绑定码。</p><ol><li>生成绑定码，有效期为 5 分钟。</li><li>使用自己的管理员微信在小程序完成绑定。</li><li>以后打开小程序自动恢复管理身份，无需重复绑定。</li></ol><button class="xr-button" :disabled="busy || ownerBound !== false" @click="issueBinding">{{ busy ? '正在生成…' : '生成一次性绑定码' }}</button><div v-if="bindingCode" class="xr-code"><span>请在 5 分钟内使用</span><code>{{ bindingCode }}</code><small>重新生成后，旧绑定码失效。请勿分享给其他人。</small></div></section>
    </template>
    <footer class="xr-footer">小染空间 · 好好生活，慢慢记录</footer>
  </main>
</template>

<style scoped>
.xr-space{--xr-accent:var(--ui-accent);--xr-border:var(--ui-border);width:100%;max-width:1180px;margin:0 auto;padding:32px;color:var(--ui-text);font-family:inherit;font-size:14px;line-height:1.65}.xr-space *{box-sizing:border-box}.xr-space h1,.xr-space h2,.xr-space h3,.xr-space p{margin:0}.xr-space h1{font-size:30px;font-weight:650;letter-spacing:1px;margin:5px 0}.xr-space h2{font-size:21px;font-weight:600}.xr-space h3{font-size:17px;font-weight:600;margin-bottom:18px}.xr-hero{display:flex;align-items:center;justify-content:space-between;gap:24px;margin:0 0 28px}.xr-hero p,.xr-muted{color:var(--ui-secondary)}.xr-eyebrow{color:var(--ui-accent-text);font-size:11px;letter-spacing:2px;font-weight:600}.xr-tabs{display:flex;gap:8px;border-bottom:1px solid var(--xr-border);margin-bottom:28px;padding-bottom:12px;overflow:auto}.xr-tabs button,.xr-kind-tabs button{display:flex;align-items:center;gap:8px;padding:10px 18px;border-radius:10px;border:0;background:transparent;color:var(--ui-secondary);white-space:nowrap;cursor:pointer;font:inherit}.xr-tabs button svg{width:19px;height:19px}.xr-tabs .active,.xr-kind-tabs .active{background:var(--ui-tint);color:var(--ui-accent-text);font-weight:600}.xr-card{border:1px solid var(--xr-border);border-radius:18px;background:var(--ui-surface);padding:26px;margin-bottom:22px;box-shadow:0 3px 14px var(--ui-shadow)}.xr-welcome{display:flex;justify-content:space-between;align-items:center;background:var(--ui-surface);padding:32px;gap:36px}.xr-welcome h2{margin:14px 0 10px;font-size:25px}.xr-welcome p{max-width:660px;color:var(--ui-secondary)}.xr-hero-icon{width:100px;height:100px;color:var(--ui-muted);flex-shrink:0}.xr-tag{display:inline-block;font-size:12px;padding:3px 10px;border-radius:20px;background:var(--ui-tint);color:var(--ui-accent-text)}.xr-actions{display:flex;gap:12px;margin-top:22px;flex-wrap:wrap}.xr-button{display:inline-flex;align-items:center;justify-content:center;min-height:40px;padding:9px 19px;border-radius:10px;background:var(--xr-accent);border:1px solid transparent;color:var(--ui-on-accent);text-decoration:none;font:inherit;font-weight:500;cursor:pointer;line-height:1.4}.xr-secondary{background:var(--ui-surface);border-color:var(--ui-border);color:var(--ui-secondary)}.xr-button:hover{filter:brightness(.97)}.xr-space button:disabled{opacity:.5;cursor:wait}.xr-space button:focus-visible,.xr-space a:focus-visible{outline:3px solid var(--ui-border);outline-offset:3px}.xr-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:20px}.xr-feature{font:inherit;text-align:left;cursor:pointer;width:100%;color:inherit}.xr-feature>svg{width:27px;height:27px;color:var(--ui-accent-text);margin-bottom:16px}.xr-feature strong,.xr-feature span{display:block}.xr-feature strong{font-size:17px;margin-bottom:4px}.xr-feature span{color:var(--ui-accent-text);font-size:13px}.xr-feature p{font-size:13px;color:var(--ui-muted);margin-top:13px}.xr-feature:hover{border-color:var(--ui-border);background:var(--ui-surface)}.xr-section-head{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:22px}.xr-section-head p{margin-top:5px}.xr-connections>div{display:grid;grid-template-columns:140px 1fr 90px;gap:16px;padding:17px 0;border-top:1px solid var(--ui-border);align-items:center}.xr-connections span{color:var(--ui-secondary)}.xr-connections em{font-size:12px;font-style:normal;color:var(--ui-secondary);background:var(--ui-tint);padding:3px 10px;text-align:center;border-radius:20px}.xr-connections .xr-local{color:var(--ui-secondary);background:var(--ui-tint)}.xr-record-layout{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1.15fr);gap:26px;align-items:start}.xr-space label{display:block;font-weight:500;margin-bottom:9px}.xr-space label span{font-weight:400;color:var(--ui-muted)}.xr-space textarea,.xr-space input:not([type=checkbox]){width:100%;font:inherit;border:1px solid var(--ui-border);border-radius:10px;background:var(--ui-surface);padding:11px 13px;color:var(--ui-text);outline:none}.xr-space input:focus,.xr-space textarea:focus{border-color:var(--ui-border);box-shadow:0 0 0 3px var(--ui-shadow)}.xr-space textarea{resize:vertical;min-height:180px}.xr-compose input{margin-bottom:22px}.xr-word-count{text-align:right;font-size:12px;color:var(--ui-muted);margin:5px 0 20px}.xr-journal article time{color:var(--ui-muted);font-size:12px;margin-right:12px}.xr-content{white-space:pre-wrap;overflow-wrap:anywhere;margin-top:16px!important;line-height:1.85}.xr-empty{padding:52px 22px;text-align:center;color:var(--ui-muted)}.xr-kind-tabs{display:flex;gap:8px;margin-bottom:25px}.xr-daily-form{display:flex;gap:12px;margin-bottom:24px;align-items:center}.xr-daily-form input{flex:1;min-width:0}.xr-daily-form .xr-button{flex-shrink:0}.xr-daily-row{padding:18px 4px;display:flex;gap:14px;align-items:center;border-top:1px solid var(--ui-border)}.xr-daily-row input{accent-color:var(--ui-accent);width:18px;height:18px;flex-shrink:0}.xr-daily-row strong{font-weight:500}.xr-daily-row p,.xr-daily-row a{font-size:13px;color:var(--ui-muted);overflow-wrap:anywhere}.xr-done{text-decoration:line-through;color:var(--ui-muted)}.xr-binding{max-width:760px;margin:0 auto;padding:38px}.xr-binding-icon{display:block;width:40px;height:40px;color:var(--ui-accent-text);margin-bottom:20px}.xr-binding h2{margin:18px 0 14px}.xr-binding p,.xr-binding ol{color:var(--ui-secondary)}.xr-binding ol{padding-left:21px;margin:24px 0}.xr-binding li{padding-bottom:10px}.xr-code{background:var(--ui-tint);border:1px dashed var(--ui-border);border-radius:12px;padding:22px;margin-top:24px}.xr-code span,.xr-code small{display:block;color:var(--ui-secondary);font-size:12px}.xr-code code{display:block;overflow-wrap:anywhere;letter-spacing:1px;font-size:21px;color:var(--ui-accent-text);margin:12px 0}.xr-alert{padding:14px 18px;border-radius:12px;margin-bottom:22px!important}.xr-error{background:var(--ui-tint);color:var(--ui-accent-text)}.xr-success{background:var(--ui-tint);color:var(--ui-accent-text)}.xr-footer{text-align:center;font-size:12px;color:var(--ui-muted);margin:36px 0 10px}@media(max-width:820px){.xr-space{padding:20px}.xr-grid,.xr-record-layout{grid-template-columns:1fr}.xr-hero-icon{display:none}.xr-connections>div{grid-template-columns:1fr 90px}.xr-connections span{grid-column:1;grid-row:auto}.xr-connections em{grid-column:2;grid-row:1/3}.xr-daily-form{flex-wrap:wrap}.xr-daily-form input{flex-basis:100%}.xr-section-head{align-items:flex-start}.xr-binding{padding:24px}.xr-welcome{padding:24px}}

.xr-daily-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:20px;margin-bottom:24px}.xr-daily-summary>div{padding:23px 26px;border:1px solid var(--ui-border);background:var(--ui-surface);border-radius:16px}.xr-daily-summary span{display:block;color:var(--ui-muted);font-size:12px;margin-bottom:10px}.xr-daily-summary strong{font-size:23px;color:var(--ui-secondary);font-weight:500}.xr-daily-summary small{font-size:12px;font-weight:400;color:var(--ui-muted)}.xr-daily-workspace{display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:24px;align-items:start}.xr-daily-main{padding:26px;min-width:0}.xr-list-intro h3{margin-bottom:5px}.xr-list-intro p{font-size:13px;color:var(--ui-muted);margin-bottom:24px}.xr-daily-form{padding:18px;background:var(--ui-soft);border:1px solid var(--ui-border);border-radius:12px;flex-wrap:wrap}.xr-daily-form input{background:var(--ui-surface)!important;min-width:140px!important}.xr-daily-form input[type=date]{flex-basis:100%}.xr-daily-empty{padding:44px 12px}.xr-daily-empty>svg{width:42px;height:42px;color:var(--ui-muted);margin:0 auto 18px}.xr-daily-empty h3{color:var(--ui-muted);font-size:16px;margin-bottom:8px}.xr-daily-empty p{font-size:13px;max-width:280px;margin:auto;line-height:1.9;color:var(--ui-muted)}.xr-daily-aside .xr-card{background:var(--ui-surface);padding:24px}.xr-aside-icon{width:27px;height:27px;color:var(--ui-muted);margin-bottom:18px}.xr-daily-aside h3{font-size:15px;margin-bottom:12px}.xr-daily-aside p,.xr-daily-aside li{font-size:13px;color:var(--ui-muted);line-height:1.9}.xr-daily-aside ol{padding-left:18px;margin:20px 0}.xr-daily-aside li{padding-bottom:10px}.xr-sync-tip{border-top:1px solid var(--ui-border);padding-top:17px;color:var(--ui-muted);font-size:12px;line-height:1.9}.xr-private-note{display:flex;gap:8px;color:var(--ui-muted);font-size:12px;padding:0 12px;line-height:1.8}.xr-private-note svg{width:18px;height:18px;flex-shrink:0}.xr-daily-row{padding:20px 4px}.xr-daily-row strong{font-size:14px}@media(max-width:1100px){.xr-daily-workspace{grid-template-columns:1fr}.xr-daily-aside .xr-card{display:none}}@media(max-width:700px){.xr-daily-summary{gap:8px}.xr-daily-summary>div{padding:16px 12px}.xr-daily-summary strong{font-size:17px}.xr-daily-main{padding:18px}}
.xr-frame-form{display:grid;gap:18px;max-width:640px}.xr-frame-form label,.xr-frame-result label{display:grid;gap:8px;font-size:14px;color:var(--ui-secondary)}.xr-frame-form input:not([type=checkbox]),.xr-frame-form textarea,.xr-frame-result input{width:100%;box-sizing:border-box;background:var(--ui-soft);border:1px solid var(--ui-border);border-radius:12px;padding:12px 16px;color:var(--ui-text)}.xr-frame-form .xr-frame-toggle{display:flex;align-items:center;gap:10px}.xr-frame-result img{width:200px;height:200px;display:block;margin:20px 0}.xr-frame-result input{max-width:420px}.xr-frame-card h2{margin-bottom:8px}
.xr-mcp-example{font-family:ui-monospace,monospace!important;font-size:12px!important;white-space:pre;line-height:1.7}.xr-tabs{flex-wrap:wrap}
.xr-mcp-onboarding h2{font-size:25px;margin:18px 0 12px}.xr-mcp-onboarding>.xr-muted{max-width:640px;line-height:1.9}.xr-agent-instruction{margin-top:26px}.xr-agent-row{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:20px 0;border-top:1px solid var(--ui-border)}.xr-agent-row p{margin:8px 0 0}.xr-mcp-advanced summary{cursor:pointer;font-weight:600;color:var(--ui-secondary)}.xr-mcp-advanced>form{margin-top:24px}
.xr-agent-permissions{display:flex;align-items:center;justify-content:flex-end;gap:14px;flex-wrap:wrap}.xr-agent-permissions label{display:flex;align-items:center;gap:8px;font-size:13px}.xr-agent-permissions>span{font-size:12px}.xr-write-settings summary{cursor:pointer;font-weight:600}.xr-write-settings select{background:var(--ui-soft);color:var(--ui-text);border:1px solid var(--ui-border);border-radius:12px;padding:12px 16px}.xr-moment-draft{padding:24px 0;border-top:1px solid var(--ui-border)}.xr-draft-content{white-space:pre-wrap;line-height:1.9;overflow-wrap:anywhere}.xr-draft-images{display:flex;gap:12px;flex-wrap:wrap;margin:20px 0}.xr-draft-images img{width:120px;height:120px;object-fit:cover;border-radius:16px}@media(max-width:700px){.xr-agent-row{flex-wrap:wrap}.xr-agent-permissions{justify-content:flex-start}.xr-draft-images img{width:90px;height:90px}}
</style>
