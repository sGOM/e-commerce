package com.example.starter.domain.address.dto

import com.example.starter.domain.address.entity.UserAddress
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 배송지 등록/수정 요청. */
data class AddressRequest(
    @field:Size(max = 50) val label: String? = null,
    @field:NotBlank @field:Size(max = 100) val receiverName: String?,
    @field:NotBlank @field:Size(max = 30) val receiverPhone: String?,
    @field:NotBlank @field:Size(max = 10) val zipcode: String?,
    @field:NotBlank @field:Size(max = 255) val address1: String?,
    @field:Size(max = 255) val address2: String? = null,
    /** 등록 시에만 사용한다. 수정은 기본 여부를 바꾸지 않는다(`PATCH /{id}/default` 사용). */
    val isDefault: Boolean = false,
)

data class AddressResponse(
    val addressId: Long,
    val label: String?,
    val receiverName: String,
    val receiverPhone: String,
    val zipcode: String,
    val address1: String,
    val address2: String?,
    val isDefault: Boolean,
) {
    companion object {
        fun from(address: UserAddress) = AddressResponse(
            addressId = requireNotNull(address.id),
            label = address.label,
            receiverName = address.receiverName,
            receiverPhone = address.receiverPhone,
            zipcode = address.zipcode,
            address1 = address.address1,
            address2 = address.address2,
            isDefault = address.isDefault,
        )
    }
}
