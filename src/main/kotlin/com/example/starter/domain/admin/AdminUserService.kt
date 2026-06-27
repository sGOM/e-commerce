package com.example.starter.domain.admin

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.AdminUserResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.admin.dto.UserSearchCondition
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.entity.UserStatus
import com.example.starter.domain.user.repository.RoleRepository
import com.example.starter.domain.user.repository.UserRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자용 사용자 관리. 검색은 **Kotlin JDSL** 로 동적 쿼리를 구성한다
 * (조건이 null 이면 자동으로 where 절에서 제외).
 */
@Service
@Transactional(readOnly = true)
class AdminUserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
) {

    fun search(condition: UserSearchCondition, pageable: Pageable): PageResponse<AdminUserResponse> {
        val page = userRepository.findPage(pageable) {
            select(entity(User::class))
                .from(entity(User::class))
                .whereAnd(
                    condition.keyword?.let { path(User::email).like("%$it%") },
                    condition.status?.let { path(User::status).eq(it) },
                )
                .orderBy(path(User::id).desc())
        }
        // JDSL 의 entity select 결과 원소는 nullable 타입이나 단일 엔티티 조회라 null 이 아니다.
        return PageResponse.of(page) { AdminUserResponse.from(it!!) }
    }

    fun get(id: Long): AdminUserResponse = AdminUserResponse.from(findUser(id))

    @Transactional
    fun changeStatus(id: Long, status: UserStatus): AdminUserResponse {
        val user = findUser(id)
        user.status = status
        return AdminUserResponse.from(user)
    }

    @Transactional
    fun grantRole(id: Long, roleName: String): AdminUserResponse {
        val user = findUser(id)
        val role = roleRepository.findByName(roleName)
            ?: throw BusinessException(ErrorCode.INVALID_INPUT, "존재하지 않는 역할: $roleName")
        user.grantRole(role)
        return AdminUserResponse.from(user)
    }

    @Transactional
    fun revokeRole(id: Long, roleName: String): AdminUserResponse {
        val user = findUser(id)
        val role = roleRepository.findByName(roleName)
            ?: throw BusinessException(ErrorCode.INVALID_INPUT, "존재하지 않는 역할: $roleName")
        user.revokeRole(role)
        return AdminUserResponse.from(user)
    }

    private fun findUser(id: Long): User =
        userRepository.findById(id).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
}
