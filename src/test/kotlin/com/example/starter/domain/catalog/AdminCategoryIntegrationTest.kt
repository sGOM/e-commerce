package com.example.starter.domain.catalog

import com.example.starter.domain.catalog.entity.Category
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.repository.CategoryRepository
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class AdminCategoryIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var categoryRepository: CategoryRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var userRepository: UserRepository

    private fun update(id: Long, body: String) = mockMvc.put("/api/admin/categories/$id") {
        with(user("admin").roles("ADMIN")); with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun remove(id: Long) = mockMvc.delete("/api/admin/categories/$id") {
        with(user("admin").roles("ADMIN")); with(csrf())
    }

    @Test
    fun `관리자는 카테고리 이름과 상위와 정렬을 수정한다`() {
        val clothing = categoryRepository.save(Category(name = "의류"))
        val outer = categoryRepository.save(Category(name = "겉옷"))

        update(outer.id!!, """{"name":"아우터","parentId":${clothing.id},"sortOrder":3}""").andExpect {
            status { isOk() }
            jsonPath("$.data.name") { value("아우터") }
            jsonPath("$.data.parentId") { value(clothing.id!!.toInt()) }
            jsonPath("$.data.sortOrder") { value(3) }
        }
    }

    @Test
    fun `자기 자신이나 하위 카테고리를 상위로 지정할 수 없다`() {
        val clothing = categoryRepository.save(Category(name = "의류"))
        val outer = categoryRepository.save(Category(name = "아우터", parent = clothing))

        update(clothing.id!!, """{"name":"의류","parentId":${clothing.id}}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("CATALOG-006") }
        }
        update(clothing.id!!, """{"name":"의류","parentId":${outer.id}}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("CATALOG-006") }
        }
    }

    @Test
    fun `하위 카테고리나 상품이 있으면 삭제할 수 없고 비어 있으면 삭제된다`() {
        val clothing = categoryRepository.save(Category(name = "의류"))
        val outer = categoryRepository.save(Category(name = "아우터", parent = clothing))
        val u = userRepository.save(User(email = "cat-del-${System.nanoTime()}@seller.com", password = "{noop}x", name = "s"))
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = "cat-del-${System.nanoTime()}", status = SellerStatus.ACTIVE))
        productRepository.save(Product(seller = seller, name = "코트", basePrice = 1000, category = outer))

        remove(clothing.id!!).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CATALOG-007") }
        }
        remove(outer.id!!).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CATALOG-007") }
        }

        val empty = categoryRepository.save(Category(name = "빈 카테고리"))
        remove(empty.id!!).andExpect { status { isOk() } }
        remove(empty.id!!).andExpect { status { isNotFound() } }
    }

    @Test
    fun `일반 사용자는 카테고리를 수정하거나 삭제할 수 없다`() {
        val c = categoryRepository.save(Category(name = "의류"))
        mockMvc.delete("/api/admin/categories/${c.id}") {
            with(user("u").roles("USER")); with(csrf())
        }.andExpect { status { isForbidden() } }
    }
}
