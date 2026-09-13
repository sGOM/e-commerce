package com.example.starter.domain.flashsale

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.repository.ProductOptionRepository
import com.example.starter.domain.flashsale.dto.AdminFlashSaleSearchCondition
import com.example.starter.domain.flashsale.dto.CreateFlashSaleRequest
import com.example.starter.domain.flashsale.dto.FlashSaleResponse
import com.example.starter.domain.flashsale.entity.FlashSale
import com.example.starter.domain.flashsale.entity.FlashSalePhase
import com.example.starter.domain.flashsale.entity.FlashSaleStatus
import com.example.starter.domain.flashsale.repository.FlashSaleRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.repository.SellerRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 타임딜(한정특가) 편성/조회. 등록은 셀러(본인 상품만) 또는 관리자(전체) 가 할 수 있고,
 * 강제 종료는 관리자 전용이다(오픈 이슈 #1 — 아래 클래스 문서 참고).
 *
 * ## 오픈 이슈 결정 (`docs/planning/flash-sale.md` §9)
 * 1. **셀러 직접 신청 vs 관리자 전용** → 셀러 자율 신청을 허용하되(승인 대기 상태 없이 즉시 활성화),
 *    관리자는 언제든 강제 종료할 수 있는 사후 통제 권한을 가진다. 승인제(사전 통제)는 안전하지만
 *    운영 리소스가 필요하고, 이 도메인은 "셀러 본인 상품 + 셀러 본인 부담"이라 리스크가 상품 등록과
 *    비슷한 수준으로 낮다고 판단했다(§4 정산 영향 항목의 "셀러 부담" 가정과도 일관).
 * 2. **할인분 부담 주체** → 셀러 100% 부담. [com.example.starter.domain.settlement.SettlementService.generate]
 *    는 `SubOrder.subtotal`(실제 결제된 특가 기준 소계)로 판매액을 집계하므로, 타임딜 특가로 판매된
 *    금액은 자동으로 더 낮은 판매액·수수료로 정산된다 — 별도 보전 로직 없이 "셀러가 할인분만큼 매출도
 *    수수료도 함께 줄어든다"가 자연히 성립한다. 플랫폼 보전(프로모션 예산)은 범위 밖으로 유보.
 * 3. **쿠폰과의 중첩 허용 여부** → 허용. 쿠폰은 상품가가 아닌 주문 총액(`Order.totalAmount`) 기준
 *    할인이므로 타임딜 특가가 반영된 `SubOrder.subtotal` 위에 쿠폰이 자연스럽게 추가 적용된다.
 *    별도 배제 로직을 두지 않는다.
 */
@Service
@Transactional(readOnly = true)
class FlashSaleService(
    private val flashSaleRepository: FlashSaleRepository,
    private val productOptionRepository: ProductOptionRepository,
    private val sellerRepository: SellerRepository,
) {

    /** 셀러 타임딜 신청 — 본인 상점의 옵션만 등록할 수 있다. */
    @Transactional
    fun createForSeller(userId: Long, request: CreateFlashSaleRequest): FlashSaleResponse {
        val seller = sellerRepository.findByUserId(userId) ?: throw BusinessException(ErrorCode.SELLER_NOT_FOUND)
        return create(request, ownerSeller = seller)
    }

    /** 관리자 직접 등록 — 판매자 소유권 검증 없이 모든 옵션에 등록 가능. */
    @Transactional
    fun createByAdmin(request: CreateFlashSaleRequest): FlashSaleResponse = create(request, ownerSeller = null)

    private fun create(request: CreateFlashSaleRequest, ownerSeller: Seller?): FlashSaleResponse {
        val optionId = requireNotNull(request.productOptionId)
        val option = productOptionRepository.findWithProductAndInventoryById(optionId)
            .orElseThrow { BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND) }
        // 타 판매자 소유 옵션은 존재를 숨겨 NOT_FOUND 로 응답(SellerProductService.ownedProduct 관례).
        if (ownerSeller != null && option.product.seller.id != ownerSeller.id) {
            throw BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND)
        }
        val startAt = requireNotNull(request.startAt)
        val endAt = requireNotNull(request.endAt)
        if (!endAt.isAfter(startAt)) {
            throw BusinessException(ErrorCode.FLASH_SALE_INVALID_PERIOD)
        }
        val salePrice = requireNotNull(request.salePrice)
        val originalPrice = option.product.basePrice + option.additionalPrice
        if (salePrice >= originalPrice) {
            throw BusinessException(ErrorCode.FLASH_SALE_INVALID_PRICE)
        }
        // AC2: 같은 옵션에 시간이 겹치는 진행 중 딜은 등록 불가.
        if (flashSaleRepository.countOverlapping(optionId, startAt, endAt) > 0) {
            throw BusinessException(ErrorCode.FLASH_SALE_PERIOD_OVERLAP)
        }
        val flashSale = flashSaleRepository.save(
            FlashSale(
                productOptionId = optionId,
                sellerId = requireNotNull(option.product.seller.id),
                originalPrice = originalPrice,
                salePrice = salePrice,
                limitQuantity = requireNotNull(request.limitQuantity),
                startAt = startAt,
                endAt = endAt,
            ),
        )
        return FlashSaleResponse.from(flashSale, option)
    }

    /** 관리자 강제 종료. 이미 강제 종료된 딜은 거부한다(멱등 보호). */
    @Transactional
    fun cancelByAdmin(id: Long): FlashSaleResponse {
        val flashSale = findById(id)
        if (flashSale.status == FlashSaleStatus.CANCELED) {
            throw BusinessException(ErrorCode.FLASH_SALE_ALREADY_ENDED)
        }
        flashSale.cancel()
        return FlashSaleResponse.from(flashSale, findOption(flashSale.productOptionId))
    }

    /** 공개 목록(AC3) — 진행 중인 딜만, 종료 임박순. */
    fun listPublicOngoing(): List<FlashSaleResponse> {
        val now = Instant.now()
        return toResponses(flashSaleRepository.findOngoing(now), now)
    }

    /** 공개 상세(AC3) — 진행 중이 아니면 존재하지 않는 것으로 취급한다([CollectionService] 관례). */
    fun getPublicDetail(id: Long): FlashSaleResponse {
        val now = Instant.now()
        val flashSale = findById(id)
        if (flashSale.phaseAt(now) != FlashSalePhase.ONGOING) {
            throw BusinessException(ErrorCode.FLASH_SALE_NOT_FOUND)
        }
        return FlashSaleResponse.from(flashSale, findOption(flashSale.productOptionId), now)
    }

    /** 셀러 본인 타임딜 목록(전체 상태 포함, 신청 현황 확인용). */
    fun listForSeller(userId: Long): List<FlashSaleResponse> {
        val seller = sellerRepository.findByUserId(userId) ?: throw BusinessException(ErrorCode.SELLER_NOT_FOUND)
        val now = Instant.now()
        return toResponses(flashSaleRepository.findBySellerIdOrderByIdDesc(requireNotNull(seller.id)), now)
    }

    /** 관리자 검색(상태/판매자 조건). */
    fun searchForAdmin(condition: AdminFlashSaleSearchCondition, pageable: Pageable): PageResponse<FlashSaleResponse> {
        val page = flashSaleRepository.findPage(pageable) {
            select(entity(FlashSale::class))
                .from(entity(FlashSale::class))
                .whereAnd(
                    condition.status?.let { path(FlashSale::status).eq(it) },
                    condition.sellerId?.let { path(FlashSale::sellerId).eq(it) },
                )
                .orderBy(path(FlashSale::id).desc())
        }
        val now = Instant.now()
        val flashSales = page.content.filterNotNull()
        val optionById = optionsByIdFor(flashSales)
        return PageResponse.of(page) { fs ->
            val option = optionById.getValue(requireNotNull(fs).productOptionId)
            FlashSaleResponse.from(fs, option, now)
        }
    }

    fun getDetailForAdmin(id: Long): FlashSaleResponse {
        val flashSale = findById(id)
        return FlashSaleResponse.from(flashSale, findOption(flashSale.productOptionId))
    }

    private fun findById(id: Long): FlashSale =
        flashSaleRepository.findById(id).orElseThrow { BusinessException(ErrorCode.FLASH_SALE_NOT_FOUND) }

    private fun findOption(optionId: Long): ProductOption =
        productOptionRepository.findWithProductAndSellerByIdIn(listOf(optionId)).firstOrNull()
            ?: throw BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND)

    private fun optionsByIdFor(flashSales: List<FlashSale>): Map<Long, ProductOption> {
        if (flashSales.isEmpty()) return emptyMap()
        val optionIds = flashSales.map { it.productOptionId }.distinct()
        return productOptionRepository.findWithProductAndSellerByIdIn(optionIds).associateBy { requireNotNull(it.id) }
    }

    private fun toResponses(flashSales: List<FlashSale>, now: Instant): List<FlashSaleResponse> {
        val optionById = optionsByIdFor(flashSales)
        // 옵션이 삭제된 경우(이론상) 방어적으로 제외한다.
        return flashSales.mapNotNull { fs -> optionById[fs.productOptionId]?.let { FlashSaleResponse.from(fs, it, now) } }
    }
}
