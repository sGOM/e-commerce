package com.example.starter.domain.upload

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse

/** S3 저장소는 키 접두사·Content-Type 을 붙여 올리고, 없는 키는 null 로 돌려준다(외부 통신 없이 S3Client 대역). */
class S3ImageStorageTest {

    private val s3 = mockk<S3Client>()
    private val storage = S3ImageStorage(s3, bucket = "shop-images", prefix = "uploads/")

    @Test
    fun `저장은 버킷에 접두사가 붙은 키와 이미지 Content-Type 으로 올린다`() {
        val request = slot<PutObjectRequest>()
        val body = slot<RequestBody>()
        every { s3.putObject(capture(request), capture(body)) } returns PutObjectResponse.builder().build()

        storage.save("a.png", byteArrayOf(1, 2, 3), ImageType.PNG)

        assertEquals("shop-images", request.captured.bucket())
        assertEquals("uploads/a.png", request.captured.key())
        assertEquals("image/png", request.captured.contentType())
        assertArrayEquals(byteArrayOf(1, 2, 3), body.captured.contentStreamProvider().newStream().readAllBytes())
    }

    @Test
    fun `조회는 객체 내용을 돌려주고 없는 키는 null 이다`() {
        every { s3.getObject(match<GetObjectRequest> { it.key() == "uploads/a.png" }) } returns
            ResponseInputStream(GetObjectResponse.builder().build(), AbortableInputStream.create(byteArrayOf(9, 8).inputStream()))
        every { s3.getObject(match<GetObjectRequest> { it.key() == "uploads/none.png" }) } throws NoSuchKeyException.builder().build()

        assertArrayEquals(byteArrayOf(9, 8), storage.load("a.png")!!.inputStream.readAllBytes())
        assertNull(storage.load("none.png"))
    }
}
