package com.example.starter.domain.catalog

import com.example.starter.domain.catalog.entity.Category
import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
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
    fun `가격 범위로 거르고 가격순으로 정렬한다`() {
        val seller = seedSeller("catalog-store-e")
        seedProduct(seller, "정렬A", 30_000, ProductStatus.ON_SALE, "SKU-SORT-A")
        seedProduct(seller, "정렬B", 10_000, ProductStatus.ON_SALE, "SKU-SORT-B")
        seedProduct(seller, "정렬C", 20_000, ProductStatus.ON_SALE, "SKU-SORT-C")

        mockMvc.get("/api/products?keyword=정렬&sort=PRICE_ASC").andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].name") { value("정렬B") }
            jsonPath("$.data.content[2].name") { value("정렬A") }
        }
        mockMvc.get("/api/products?keyword=정렬&sort=PRICE_DESC").andExpect {
            jsonPath("$.data.content[0].name") { value("정렬A") }
        }
        mockMvc.get("/api/products?keyword=정렬&minPrice=15000&maxPrice=30000&sort=PRICE_ASC").andExpect {
            jsonPath("$.data.totalElements") { value(2) }
            jsonPath("$.data.content[0].name") { value("정렬C") }
        }
    }

    @Test
    fun `평점순으로 정렬한다`() {
        val seller = seedSeller("catalog-store-f")
        val low = seedProduct(seller, "평점낮음", 10_000, ProductStatus.ON_SALE, "SKU-RATE-LOW")
        val high = seedProduct(seller, "평점높음", 10_000, ProductStatus.ON_SALE, "SKU-RATE-HIGH")
        low.updateReviewSummary(java.math.BigDecimal("3.5"), 2)
        high.updateReviewSummary(java.math.BigDecimal("4.8"), 5)
        productRepository.saveAll(listOf(low, high))

        mockMvc.get("/api/products?keyword=평점&sort=RATING_DESC").andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].name") { value("평점높음") }
            jsonPath("$.data.content[1].name") { value("평점낮음") }
        }
    }

    @Test
    fun `상위 카테고리로 검색하면 하위 카테고리 상품도 포함된다`() {
        val seller = seedSeller("catalog-store-g")
        val clothing = categoryRepository.save(Category(name = "의류", sortOrder = 1))
        val outer = categoryRepository.save(Category(name = "아우터", parent = clothing, sortOrder = 1))
        val padding = categoryRepository.save(Category(name = "패딩류", parent = outer, sortOrder = 1))
        val food = categoryRepository.save(Category(name = "식품", sortOrder = 2))
        seedProduct(seller, "티셔츠", 10_000, ProductStatus.ON_SALE, "SKU-TREE-1", category = clothing)
        seedProduct(seller, "코트", 10_000, ProductStatus.ON_SALE, "SKU-TREE-2", category = outer)
        seedProduct(seller, "롱패딩", 10_000, ProductStatus.ON_SALE, "SKU-TREE-3", category = padding)
        seedProduct(seller, "사과", 10_000, ProductStatus.ON_SALE, "SKU-TREE-4", category = food)

        mockMvc.get("/api/products?categoryId=${clothing.id}").andExpect {
            status { isOk() }
            jsonPath("$.data.totalElements") { value(3) }
        }
        mockMvc.get("/api/products?categoryId=${outer.id}").andExpect {
            jsonPath("$.data.totalElements") { value(2) }
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
