package com.example.starter.common.bootstrap

import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.RoleRepository
import com.example.starter.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 로컬 개발 편의를 위한 관리자 계정 부트스트랩.
 * **local 프로파일에서만** 동작하며, 이미 존재하면 아무 것도 하지 않는다(멱등).
 * 운영에서는 별도 절차로 관리자를 생성한다.
 */
@Component
@Profile("local")
class AdminBootstrap(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) return

        val adminRole = roleRepository.findByName("ROLE_ADMIN")
            ?: run {
                log.warn("ROLE_ADMIN 이 없어 관리자 부트스트랩을 건너뜁니다.")
                return
            }
        val admin = User(
            email = ADMIN_EMAIL,
            password = passwordEncoder.encode(ADMIN_PASSWORD),
            name = "관리자",
        )
        admin.grantRole(adminRole)
        userRepository.save(admin)
        log.info("[local] 관리자 계정 생성됨: {} / {}", ADMIN_EMAIL, ADMIN_PASSWORD)
    }

    private companion object {
        const val ADMIN_EMAIL = "admin@example.com"
        const val ADMIN_PASSWORD = "Admin1234!"
    }
}
