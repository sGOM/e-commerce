package com.example.starter.domain.wishlist

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.notification.NotificationService
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.wishlist.dto.WishlistResponse
import com.example.starter.domain.wishlist.entity.Wishlist
import com.example.starter.domain.wishlist.repository.WishlistRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 위시리스트(찜) 담기/빼기/조회 및 가격 인하 알림 트리거 처리(`docs/planning/wishlist-price-alert.md`).
 */
@Service
@Transactional(readOnly = true)
class WishlistService(
    private val wishlistRepository: WishlistRepository,
    private val productRepository: ProductRepository,
    private val notificationService: NotificationService,
) {

    companion object {
        /** 회원당 위시리스트 최대 개수(§4, 어뷰징/성능 보호 — 정확한 수치는 오픈 이슈, 잠정값). */
        const val MAX_WISHLIST_SIZE = 500
    }

    /** 위시리스트 담기(US-1/AC1). 중복 담기(AC2), 상한 초과는 거절한다. */
    @Transactional
    fun add(userId: Long, productId: Long): WishlistResponse {
        val product = productRepository.findById(productId)
            .orElseThrow { BusinessException(ErrorCode.PRODUCT_NOT_FOUND) }
        if (wishlistRepository.existsByUserIdAndProductId(userId, productId)) {
            throw BusinessException(ErrorCode.WISHLIST_ALREADY_EXISTS)
        }
        if (wishlistRepository.countByUserId(userId) >= MAX_WISHLIST_SIZE) {
            throw BusinessException(ErrorCode.WISHLIST_LIMIT_EXCEEDED)
        }
        val wishlist = wishlistRepository.save(
            Wishlist(userId = userId, productId = productId, baselinePrice = product.basePrice),
        )
        return WishlistResponse.from(wishlist, product)
    }

    /** 위시리스트 빼기. 본인 항목만 삭제 가능 — 없으면 404. */
    @Transactional
    fun remove(userId: Long, productId: Long) {
        val wishlist = wishlistRepository.findByUserIdAndProductId(userId, productId)
            .orElseThrow { BusinessException(ErrorCode.WISHLIST_NOT_FOUND) }
        wishlistRepository.delete(wishlist)
    }

    /**
     * 내 위시리스트 목록(마이페이지, US-2). [priceDropOnly] 이면 현재 인하 상태인 항목만 본다.
     * 상품이 완전 삭제된 항목은 조용히 걸러내고(§4 자동 정리), 판매중지/품절은 상태 라벨로 노출한다(AC6).
     */
    fun getMyWishlist(userId: Long, priceDropOnly: Boolean, pageable: Pageable): PageResponse<WishlistResponse> {
        val page = if (priceDropOnly) {
            wishlistRepository.findPriceDroppedByUserId(userId, pageable)
        } else {
            wishlistRepository.findByUserIdOrderByIdDesc(userId, pageable)
        }
        val productsById = productRepository
            .findAllById(page.content.map { it.productId }.distinct())
            .associateBy { it.id }
        return PageResponse.of(page) { wishlist -> WishlistResponse.from(wishlist, productsById[wishlist.productId]) }
    }

    /** 셀러 상품 상세 "찜 N명" 카운트(AC13). */
    fun countByProduct(productId: Long): Long = wishlistRepository.countByProductId(productId)

    /**
     * 가격 인하 트리거([com.example.starter.domain.catalog.event.ProductPriceChangedEvent] 구독측
     * 호출 대상). 상품을 찜한 회원 중 baseline 보다 신규가가 낮은 항목만 알림을 보낸다(AC8/AC9).
     *
     * - 판매중지(HIDDEN)/삭제 상품은 발송하지 않는다(AC11).
     * - 이벤트 발행과 이 메서드 실행 사이(AFTER_COMMIT + `@Async`) 가격이 또 바뀔 수 있어, 발송 직전
     *   최신 가격을 다시 확인한다 — 이벤트의 [newPrice] 와 현재 가격이 다르면(그 사이 재변경) 스킵하고
     *   다음 이벤트가 최신 값 기준으로 재평가하게 둔다(재입고 알림의 동일 레이스 대응 원칙).
     */
    @Transactional
    fun notifyPriceDrop(productId: Long, newPrice: Long) {
        val product = productRepository.findById(productId).orElse(null) ?: return
        if (!product.status.isVisible) return
        if (product.basePrice != newPrice) return

        val targets = wishlistRepository.findByProductIdForUpdate(productId)
            .filter { it.baselinePrice > newPrice }
        if (targets.isEmpty()) return

        val now = Instant.now()
        targets.forEach { wishlist ->
            val previousBaseline = wishlist.baselinePrice
            wishlist.markNotified(newPrice, now)
            notificationService.notify(
                userId = wishlist.userId,
                type = NotificationType.PRICE_DROP,
                title = "가격 인하 알림",
                body = "'${product.name}' 상품이 ${previousBaseline}원에서 ${newPrice}원으로 인하되었습니다.",
                linkUrl = "/products/$productId",
            )
        }
    }
}
