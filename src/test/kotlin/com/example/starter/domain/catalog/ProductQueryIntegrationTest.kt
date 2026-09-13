package com.example.starter.domain.catalog

import com.example.starter.domain.catalog.entity.Category
import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.catalog.repository.CategoryRepository
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional // 각 테스트 종료 후 롤백 → 외부 DB 재사용 시에도 격리 보장
class ProductQueryIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var categoryRepository: CategoryRepository

    private fun seedSeller(storeName: String): Seller {
        val user = userRepository.save(User(email = "$storeName@seller.com", password = "{noop}x", name = storeName))
        return sellerRepository.save(
            Seller(userId = user.id!!, storeName = storeName, status = SellerStatus.ACTIVE),
        )
    }

    private fun seedProduct(
        seller: Seller,
        name: String,
        basePrice: Long,
        status: ProductStatus,
        optionSku: String,
        additionalPrice: Long = 0,
        stock: Int = 0,
        category: Category? = null,
    ): Product {
        val product = Product(seller = seller, name = name, basePrice = basePrice, status = status)
        product.category = category
        val option = ProductOption(name = "기본", sku = optionSku, additionalPrice = additionalPrice)
        option.assignInventory(Inventory(quantity = stock, reserved = 0))
        product.addOption(option)
        return productRepository.save(product)
    }

    @Test
    fun `노출 상품은 공개 검색되고 비노출 상품은 제외된다`() {
        val seller = seedSeller("catalog-store-a")
        seedProduct(seller, "검색노출상품", 10_000, ProductStatus.ON_SALE, "SKU-VISIBLE-1")
        seedProduct(seller, "검색숨김상품", 20_000, ProductStatus.HIDDEN, "SKU-HIDDEN-1")

        mockMvc.get("/api/products?keyword=검색").andExpect {
            status { isOk() }
            jsonPath("$.data.totalElements") { value(1) }
            jsonPath("$.data.content[0].name") { value("검색노출상품") }
            jsonPath("$.data.content[0].storeName") { value("catalog-store-a") }
        }
    }

    @Test
    fun `상품 상세는 최종 판매가와 가용 재고를 노출한다`() {
        val seller = seedSeller("catalog-store-b")
        val product = seedProduct(
            seller, "상세상품", 10_000, ProductStatus.ON_SALE,
            optionSku = "SKU-DETAIL-1", additionalPrice = 1_500, stock = 7,
        )

        mockMvc.get("/api/products/${product.id}").andExpect {
            status { isOk() }
            jsonPath("$.data.name") { value("상세상품") }
            jsonPath("$.data.options[0].price") { value(11_500) }
            jsonPath("$.data.options[0].availableStock") { value(7) }
        }
    }

    @Test
    fun `숨김 상품 상세 조회는 404`() {
        val seller = seedSeller("catalog-store-c")
        val product = seedProduct(seller, "숨김상세", 5_000, ProductStatus.DRAFT, "SKU-DRAFT-1")

        mockMvc.get("/api/products/${product.id}").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CATALOG-001") }
        }
    }

    @Test
    fun `카테고리로 검색하면 해당 카테고리 상품만 노출된다`() {
        val seller = seedSeller("catalog-store-d")
        val outer = categoryRepository.save(Category(name = "아우터", sortOrder = 1))
        val shoes = categoryRepository.save(Category(name = "신발", sortOrder = 2))
        seedProduct(seller, "패딩", 90_000, ProductStatus.ON_SALE, "SKU-CAT-OUTER", category = outer)
        seedProduct(seller, "운동화", 80_000, ProductStatus.ON_SALE, "SKU-CAT-SHOES", category = shoes)

        mockMvc.get("/api/products?categoryId=${outer.id}").andExpect {
            status { isOk() }
            jsonPath("$.data.totalElements") { value(1) }
            jsonPath("$.data.content[0].name") { value("패딩") }
            jsonPath("$.data.content[0].categoryName") { value("아우터") }
        }
    }

    @Test
    fun `카테고리 목록은 정렬 순서로 공개 조회된다`() {
        categoryRepository.save(Category(name = "B카테고리", sortOrder = 2))
        categoryRepository.save(Category(name = "A카테고리", sortOrder = 1))

        mockMvc.get("/api/categories").andExpect {
            status { isOk() }
            jsonPath("$.data[0].name") { value("A카테고리") }
            jsonPath("$.data[1].name") { value("B카테고리") }
        }
    }
}
