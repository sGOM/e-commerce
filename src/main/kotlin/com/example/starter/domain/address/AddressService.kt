package com.example.starter.domain.address

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.address.dto.AddressRequest
import com.example.starter.domain.address.dto.AddressResponse
import com.example.starter.domain.address.entity.UserAddress
import com.example.starter.domain.address.repository.UserAddressRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 회원 배송지 주소록. 회원당 기본 배송지는 항상 최대 1개(DB 부분 유니크 인덱스로도 보장)이고,
 * 배송지가 하나라도 있으면 기본 배송지가 존재하도록 유지한다(첫 등록 자동 기본, 기본 삭제 시 최근 항목 승계).
 */
@Service
@Transactional(readOnly = true)
class AddressService(
    private val addressRepository: UserAddressRepository,
) {

    companion object {
        const val MAX_ADDRESSES = 20
    }

    fun getMyAddresses(userId: Long): List<AddressResponse> =
        addressRepository.findByUserIdOrderByIsDefaultDescIdDesc(userId).map { AddressResponse.from(it) }

    @Transactional
    fun create(userId: Long, request: AddressRequest): AddressResponse {
        val count = addressRepository.countByUserId(userId)
        if (count >= MAX_ADDRESSES) throw BusinessException(ErrorCode.ADDRESS_LIMIT_EXCEEDED)

        val makeDefault = count == 0L || request.isDefault
        if (makeDefault) addressRepository.clearDefault(userId)
        val address = addressRepository.save(
            UserAddress(
                userId = userId,
                label = request.label?.ifBlank { null },
                receiverName = request.receiverName!!,
                receiverPhone = request.receiverPhone!!,
                zipcode = request.zipcode!!,
                address1 = request.address1!!,
                address2 = request.address2?.ifBlank { null },
                isDefault = makeDefault,
            ),
        )
        return AddressResponse.from(address)
    }

    @Transactional
    fun update(userId: Long, addressId: Long, request: AddressRequest): AddressResponse {
        val address = owned(userId, addressId)
        address.label = request.label?.ifBlank { null }
        address.receiverName = request.receiverName!!
        address.receiverPhone = request.receiverPhone!!
        address.zipcode = request.zipcode!!
        address.address1 = request.address1!!
        address.address2 = request.address2?.ifBlank { null }
        return AddressResponse.from(address)
    }

    @Transactional
    fun setDefault(userId: Long, addressId: Long): AddressResponse {
        owned(userId, addressId) // 소유 확인을 먼저 — 남의 id 로 내 기본 배송지만 해제되는 일이 없게
        addressRepository.clearDefault(userId) // 영속성 컨텍스트가 비워지므로 아래에서 다시 조회한다
        val address = owned(userId, addressId)
        address.isDefault = true
        return AddressResponse.from(address)
    }

    @Transactional
    fun delete(userId: Long, addressId: Long) {
        val address = owned(userId, addressId)
        addressRepository.delete(address)
        if (address.isDefault) {
            addressRepository.flush()
            addressRepository.findFirstByUserIdOrderByIdDesc(userId)?.isDefault = true
        }
    }

    private fun owned(userId: Long, addressId: Long): UserAddress =
        addressRepository.findByIdAndUserId(addressId, userId)
            ?: throw BusinessException(ErrorCode.ADDRESS_NOT_FOUND)
}
