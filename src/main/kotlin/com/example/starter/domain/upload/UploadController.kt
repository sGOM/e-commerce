package com.example.starter.domain.upload

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.common.response.ApiResponse
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Duration
import java.util.UUID

data class UploadResponse(val url: String)

/**
 * 이미지 업로드/조회 API (ROADMAP 2.1). 상품 대표 이미지와 리뷰 사진이 공용으로 쓴다.
 *
 * 형식은 클라이언트가 보낸 확장자·Content-Type 대신 **파일 앞부분 시그니처(magic bytes)** 로 판별하고,
 * 저장 파일명은 서버가 만든 UUID + 판별된 확장자라 경로 조작이나 HTML/SVG 위장 업로드가 불가능하다.
 * 최대 크기는 `spring.servlet.multipart.max-file-size` 로 제한한다. 저장 위치는 [ImageStorage](local/s3)가 정한다.
 */
@RestController
@RequestMapping("/api/uploads")
class UploadController(
    private val storage: ImageStorage,
) {

    @PostMapping
    fun upload(@RequestParam file: MultipartFile): ApiResponse<UploadResponse> {
        val bytes = file.bytes
        val type = ImageType.detect(bytes.copyOf(minOf(bytes.size, 12))) ?: throw BusinessException(ErrorCode.UNSUPPORTED_IMAGE)
        val name = "${UUID.randomUUID()}.${type.extension}"
        storage.save(name, bytes, type)
        return ApiResponse.success(UploadResponse("/api/uploads/$name"))
    }

    @GetMapping("/{name}")
    fun download(@PathVariable name: String): ResponseEntity<Resource> {
        // 서버가 만든 이름(UUID.확장자)만 허용 — 경로 구분자나 다른 형식은 저장소에 닿기 전에 거른다.
        val type = ImageType.entries.firstOrNull { name.endsWith(".${it.extension}") }
            ?.takeIf { NAME.matches(name) }
            ?: throw BusinessException(ErrorCode.UPLOAD_NOT_FOUND)
        val resource = storage.load(name) ?: throw BusinessException(ErrorCode.UPLOAD_NOT_FOUND)
        return ResponseEntity.ok()
            .contentType(type.mediaType)
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
            .header("X-Content-Type-Options", "nosniff")
            .body(resource)
    }

    private companion object {
        val NAME = Regex("""^[0-9a-f-]{36}\.[a-z]+$""")
    }
}

enum class ImageType(val extension: String, val mediaType: MediaType) {
    JPEG("jpg", MediaType.IMAGE_JPEG),
    PNG("png", MediaType.IMAGE_PNG),
    GIF("gif", MediaType.IMAGE_GIF),
    WEBP("webp", MediaType.parseMediaType("image/webp")),
    ;

    companion object {
        fun detect(b: ByteArray): ImageType? {
            fun at(offset: Int, vararg sig: Int) =
                b.size >= offset + sig.size && sig.indices.all { b[offset + it] == sig[it].toByte() }
            return when {
                at(0, 0xFF, 0xD8, 0xFF) -> JPEG
                at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> PNG
                at(0, 0x47, 0x49, 0x46, 0x38) -> GIF
                at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50) -> WEBP
                else -> null
            }
        }
    }
}
