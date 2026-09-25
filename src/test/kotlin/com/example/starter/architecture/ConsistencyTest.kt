package com.example.starter.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 문서에만 있던 규칙 중 기계적으로 판정할 수 있는 것을 CI 에서 강제한다(스프링 컨텍스트 없이 소스·문서를 읽는다).
 * 판단이 필요한 나머지 규칙은 `docs/CODING_CONVENTIONS.md` 가 원천이다.
 */
class ConsistencyTest {

    private val sources = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun File.rel() = invariantSeparatorsPath.substringAfter("src/main/kotlin/")

    /** CODING_CONVENTIONS §3.5 — API 요청·응답 DTO 는 `dto/` 패키지에 둔다(공통 봉투 ApiResponse 는 예외). */
    @Test
    fun `요청·응답 DTO 는 dto 패키지에 있다`() {
        val topLevelDto = Regex("""^(?:data )?class (\w+(?:Request|Response))\b""", RegexOption.MULTILINE)
        val violations = sources
            .filter { "/dto/" !in it.invariantSeparatorsPath && "/common/" !in it.invariantSeparatorsPath }
            .flatMap { f -> topLevelDto.findAll(f.readText()).map { "${f.rel()}: ${it.groupValues[1]}" } }
        assertEquals(emptyList<String>(), violations, "dto/XxxDtos.kt 로 옮기세요")
    }

    /**
     * CODING_CONVENTIONS §3.1 — 관리자·판매자 API 는 `Admin*`/`Seller*` 컨트롤러로 나누고, 한 컨트롤러에 다른 역할 경로를 섞지 않는다.
     * (`/api/seller/apply` 는 일반 회원이 입점을 신청하는 경로라 판매자 컨트롤러가 아니어도 된다.)
     */
    @Test
    fun `역할별 경로는 해당 접두어 컨트롤러에만 있다`() {
        val apiPath = Regex(""""(/api/[^"]*)"""")
        val violations = sources.filter { it.name.endsWith("Controller.kt") }.mapNotNull { f ->
            val paths = apiPath.findAll(f.readText()).map { it.groupValues[1] }.filter { it != "/api/seller/apply" }.toSet()
            val roles = paths.map { p -> ROLE_PREFIXES.entries.firstOrNull { p.startsWith(it.key) }?.value ?: "" }.toSet()
            val expected = roles.singleOrNull()
            when {
                roles.size > 1 -> "${f.rel()}: 역할이 섞임 $paths"
                expected != null && expected.isNotEmpty() && !f.name.startsWith(expected) -> "${f.rel()}: $expected 접두어 필요"
                else -> null
            }
        }
        assertEquals(emptyList<String>(), violations)
    }

    /** 설정으로 구현·스케줄러를 켜고 끄는 키는 `SERVER_ARCHITECTURE.md` §11 표에 있어야 한다(문서 동기화). */
    @Test
    fun `ConditionalOnProperty 설정 키는 서버 아키텍처 문서에 있다`() {
        val doc = File("docs/SERVER_ARCHITECTURE.md").readText()
        val conditional = Regex("""@ConditionalOnProperty\(([^)]*)\)""")
        val keys = sources.flatMap { f ->
            conditional.findAll(f.readText()).map { m ->
                val args = m.groupValues[1]
                val prefix = Regex("""prefix = "([^"]+)"""").find(args)?.groupValues?.get(1)
                val name = Regex("""name = \["([^"]+)"]""").find(args)?.groupValues?.get(1)
                    ?: Regex("""^"([^"]+)"""").find(args.trim())?.groupValues?.get(1)
                listOfNotNull(prefix, name).joinToString(".")
            }
        }.toSet()
        assertEquals(emptyList<String>(), keys.filter { "`$it`" !in doc }.sorted(), "SERVER_ARCHITECTURE.md §11 에 추가하세요")
    }

    private companion object {
        val ROLE_PREFIXES = mapOf("/api/admin" to "Admin", "/api/seller" to "Seller")
    }
}
