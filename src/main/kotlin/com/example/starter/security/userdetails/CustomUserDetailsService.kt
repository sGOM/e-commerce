package com.example.starter.security.userdetails

import com.example.starter.domain.user.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 이메일을 username 으로 사용하여 사용자를 로드한다.
 * 역할/권한을 함께 페치하여 인가 판단 시 추가 쿼리를 막는다.
 */
@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository,
) : UserDetailsService {

    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findWithRolesByEmail(username)
            ?: throw UsernameNotFoundException("사용자를 찾을 수 없습니다: $username")
        return CustomUserDetails(user)
    }
}
