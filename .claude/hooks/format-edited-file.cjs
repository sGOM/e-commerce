// PostToolUse(Edit|Write) 훅: 편집된 프론트 파일을 prettier 로 즉시 포맷한다(규칙: frontend/.prettierrc.json).
// Kotlin 은 Gradle 기동 비용이 커서 여기서 하지 않는다 — 커밋 전 ./gradlew ktlintFormat, CI 가 ktlintCheck 로 막는다.
// 어떤 경우에도 편집을 막지 않도록 항상 exit 0.
const { spawnSync } = require('node:child_process')
const path = require('node:path')

let input = ''
process.stdin.on('data', (chunk) => (input += chunk))
process.stdin.on('end', () => {
  try {
    const { tool_input: toolInput = {}, tool_response: toolResponse = {} } = JSON.parse(input)
    const file = toolInput.file_path || toolResponse.filePath
    if (!file) return
    const root = path.resolve(__dirname, '..', '..')
    const rel = path.relative(path.join(root, 'frontend'), path.resolve(file)).split(path.sep).join('/')
    if (!rel.startsWith('src/') || rel.startsWith('src/components/ui/') || !/\.(ts|tsx|css)$/.test(rel)) return
    const prettier = path.join(root, 'frontend', 'node_modules', 'prettier', 'bin', 'prettier.cjs')
    spawnSync(process.execPath, [prettier, '--write', file], { cwd: path.join(root, 'frontend'), stdio: 'ignore' })
  } catch {
    // 훅 실패가 작업을 막지 않게 무시
  }
})
