package com.example.starter.domain.auth

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.auth.dto.SignupRequest
import com.example.starter.domain.user.entity.Role
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.RoleRepository
import com.example.starter.domain.user.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 자체 회원가입/계정 생성 로직.
 * 로그인 자체는 세션 처리가 필요해 [AuthController] 에서 [org.springframework.security.authentication.AuthenticationManager] 로 수행한다.
 */
@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    companion object {
        const val DEFAULT_ROLE = "ROLE_USER"
    }

    fun signup(request: SignupRequest): User {
        if (userRepository.existsByEmail(request.email)) {
            throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
        }
        val user = User(
            email = request.email,
            password = passwordEncoder.encode(request.password),
            name = request.name,
        )
        user.grantRole(defaultRole())
        return userRepository.save(user)
    }

    private fun defaultRole(): Role =
        roleRepository.findByName(DEFAULT_ROLE)
            ?: throw BusinessException(ErrorCode.INTERNAL_ERROR, "기본 역할($DEFAULT_ROLE)이 설정되지 않았습니다.")
}
