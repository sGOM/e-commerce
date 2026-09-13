package com.example.starter.domain.address

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.address.dto.AddressRequest
import com.example.starter.domain.address.dto.AddressResponse
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 마이페이지 배송지 주소록 API (회원 전용). */
@RestController
@RequestMapping("/api/me/addresses")
class MyAddressController(
    private val addressService: AddressService,
) {

    @GetMapping
    fun list(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<List<AddressResponse>> =
        ApiResponse.success(addressService.getMyAddresses(principal.userId))

    @PostMapping
    fun create(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: AddressRequest,
    ): ApiResponse<AddressResponse> =
        ApiResponse.success(addressService.create(principal.userId, request), "배송지를 등록했습니다.")

    @PutMapping("/{addressId}")
    fun update(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable addressId: Long,
        @RequestBody @Valid request: AddressRequest,
    ): ApiResponse<AddressResponse> =
        ApiResponse.success(addressService.update(principal.userId, addressId, request), "배송지를 수정했습니다.")

    @PatchMapping("/{addressId}/default")
    fun setDefault(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable addressId: Long,
    ): ApiResponse<AddressResponse> =
        ApiResponse.success(addressService.setDefault(principal.userId, addressId), "기본 배송지로 지정했습니다.")

    @DeleteMapping("/{addressId}")
    fun delete(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable addressId: Long,
    ): ApiResponse<Unit> {
        addressService.delete(principal.userId, addressId)
        return ApiResponse.success("배송지를 삭제했습니다.")
    }
}
