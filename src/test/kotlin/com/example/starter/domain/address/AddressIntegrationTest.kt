package com.example.starter.domain.address

import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.security.userdetails.CustomUserDetails
import com.example.starter.support.AbstractIntegrationTest
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class AddressIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository

    private fun seedMember(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    private fun body(label: String, isDefault: Boolean = false, receiverName: String = "홍길동") =
        """{"label":"$label","receiverName":"$receiverName","receiverPhone":"010-1234-5678","zipcode":"06236","address1":"서울 강남구 테헤란로 1","address2":"101호","isDefault":$isDefault}"""

    private fun create(member: CustomUserDetails, label: String, isDefault: Boolean = false): Long {
        val res = mockMvc.post("/api/me/addresses") {
            with(user(member)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body(label, isDefault)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Regex(""""addressId":(\d+)""").find(res)!!.groupValues[1].toLong()
    }

    @Test
    fun `첫 배송지는 자동으로 기본 배송지가 되고 목록은 기본 배송지가 먼저 온다`() {
        val member = seedMember("addr-1@example.com")
        create(member, "집")
        create(member, "회사")

        mockMvc.get("/api/me/addresses") { with(user(member)) }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(2) }
            jsonPath("$.data[0].label") { value("집") }
            jsonPath("$.data[0].isDefault") { value(true) }
            jsonPath("$.data[1].label") { value("회사") }
            jsonPath("$.data[1].isDefault") { value(false) }
        }
    }

    @Test
    fun `기본 배송지를 바꾸면 기존 기본 배송지는 해제된다`() {
        val member = seedMember("addr-2@example.com")
        create(member, "집")
        val office = create(member, "회사")

        mockMvc.patch("/api/me/addresses/$office/default") {
            with(user(member)); with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.isDefault") { value(true) }
        }
        // 생성 시 isDefault=true 도 기존 기본 배송지를 대체한다
        create(member, "본가", isDefault = true)

        mockMvc.get("/api/me/addresses") { with(user(member)) }.andExpect {
            jsonPath("$.data[0].label") { value("본가") }
            jsonPath("$.data[?(@.isDefault == true)]") { value(hasSize<Any>(1)) }
        }
    }

    @Test
    fun `기본 배송지를 삭제하면 가장 최근 배송지가 기본이 된다`() {
        val member = seedMember("addr-3@example.com")
        val home = create(member, "집")
        create(member, "회사")
        create(member, "헬스장")

        mockMvc.delete("/api/me/addresses/$home") {
            with(user(member)); with(csrf())
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/me/addresses") { with(user(member)) }.andExpect {
            jsonPath("$.data.length()") { value(2) }
            jsonPath("$.data[0].label") { value("헬스장") }
            jsonPath("$.data[0].isDefault") { value(true) }
        }
    }

    @Test
    fun `배송지를 수정한다`() {
        val member = seedMember("addr-4@example.com")
        val id = create(member, "집")

        mockMvc.put("/api/me/addresses/$id") {
            with(user(member)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body("새집", receiverName = "김철수")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.label") { value("새집") }
            jsonPath("$.data.receiverName") { value("김철수") }
            jsonPath("$.data.isDefault") { value(true) } // 수정은 기본 여부를 바꾸지 않는다
        }
    }

    @Test
    fun `다른 회원의 배송지는 수정하거나 삭제할 수 없다`() {
        val owner = seedMember("addr-owner@example.com")
        val other = seedMember("addr-other@example.com")
        val id = create(owner, "집")

        mockMvc.put("/api/me/addresses/$id") {
            with(user(other)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body("탈취")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ADDRESS-001") }
        }
        mockMvc.delete("/api/me/addresses/$id") {
            with(user(other)); with(csrf())
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `필수 항목이 비어 있으면 400`() {
        val member = seedMember("addr-5@example.com")
        mockMvc.post("/api/me/addresses") {
            with(user(member)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body("집", receiverName = "")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `회원당 배송지는 최대 20개까지 등록할 수 있다`() {
        val member = seedMember("addr-6@example.com")
        repeat(20) { create(member, "주소$it") }

        mockMvc.post("/api/me/addresses") {
            with(user(member)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body("초과")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ADDRESS-002") }
        }
    }
}
