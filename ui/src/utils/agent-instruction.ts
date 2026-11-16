export function buildAgentInstruction(origin: string, code: string): string {
  const url = new URL(origin)
  if (url.protocol !== 'https:' || url.origin !== origin || !/^[\w-]{24}$/.test(code)) throw new Error('接入信息格式不正确')
  const base = origin + '/apis/api.space.onoe.cn/v1alpha1'
  return `请帮我把这个博客接入你当前 Agent 的 MCP，使用你自身支持的配置方式完成。

1. 用 HTTP 工具向 ${base}/mcpenrollments 发送一次 POST。
Content-Type: application/json
请求体：${JSON.stringify({ code, agentName: '请替换为你当前 Agent 的名称' })}
这是五分钟内有效的一次性接入码。请求成功后不要再次兑换；如已使用或过期，请让我回 Halo 重新生成指令。

2. 将响应中的 token 保存到当前 Agent 的本地私密 MCP 配置，设置仅当前用户可读。不要在聊天回复、终端输出或日志中打印响应原文或凭证。
连接名称：xiaoran-frame
连接方式：Streamable HTTP
MCP 地址：${base}/mcp
请求头：X-Frame-MCP-Token，值使用响应中的 token。
保留我现有的其他 MCP 配置；已有同一博客的连接时，只更新该项。不要执行远程安装脚本。

3. 重新加载此 MCP 连接，执行 tools/list，再调用 site_info 自测。确认后只回复博客名称、可用工具和连接状态，不要回显凭证。默认可搜索与读取公开文章。需要上传图片和写瞬间时，请用户在Halo的已连接Agent中为你开启写入权限，再重新获取tools/list。可调用upload_image上传图片（本地文件先无参调用获取multipart方式），正文与图片先在对话中展示并获得用户明确确认，再调用publish_moment，设置confirm_public=true直接发布。用户无需回Halo再次审批。使用唯一request_id防止重复，失败时复用原ID和内容重试；只有返回PUBLISHED才能报告已公开。

如果你没有修改 MCP 配置的权限，或当前 Agent 不支持该连接方式，请告诉我还需要的最少操作。需要重启才能生效时请明确提示，不要把“配置已保存”说成“连接已成功”。`
}
