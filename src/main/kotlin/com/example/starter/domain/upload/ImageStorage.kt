package com.example.starter.domain.upload

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * 업로드 이미지 저장소. `upload.storage` 로 구현을 고른다(local 기본, s3).
 * 공개 URL 은 저장소와 무관하게 `/api/uploads/{name}` 이라, 저장소를 바꿔도 DB 의 이미지 URL 이 그대로 유효하다.
 * [name] 은 서버가 만든 `UUID.확장자` 뿐이다(UploadController 가 형식을 검증한다).
 */
interface ImageStorage {
    fun save(name: String, bytes: ByteArray, type: ImageType)

    /** 없으면 null. */
    fun load(name: String): Resource?
}

/** 로컬 디스크 저장. 단일 인스턴스·영속 볼륨(`UPLOAD_DIR`) 환경용. */
@Component
@ConditionalOnProperty(prefix = "upload", name = ["storage"], havingValue = "local", matchIfMissing = true)
class LocalImageStorage(
    @Value("\${upload.dir:uploads}") dir: String,
) : ImageStorage {
    private val root: Path = Path.of(dir).toAbsolutePath().normalize().also { Files.createDirectories(it) }

    override fun save(name: String, bytes: ByteArray, type: ImageType) {
        Files.write(root.resolve(name), bytes)
    }

    override fun load(name: String): Resource? {
        val path = root.resolve(name).normalize()
        return if (path.parent == root && Files.isRegularFile(path)) FileSystemResource(path) else null
    }
}

/**
 * S3(또는 MinIO 등 S3 호환) 저장. 다중 인스턴스·재배포에도 파일이 유지된다.
 * 자격 증명은 AWS 기본 공급자 체인(환경변수 `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, IAM 역할 등)을 쓴다.
 */
class S3ImageStorage(
    private val s3: S3Client,
    private val bucket: String,
    private val prefix: String,
) : ImageStorage {

    override fun save(name: String, bytes: ByteArray, type: ImageType) {
        s3.putObject(
            PutObjectRequest.builder().bucket(bucket).key(prefix + name).contentType(type.mediaType.toString()).build(),
            RequestBody.fromBytes(bytes),
        )
    }

    override fun load(name: String): Resource? = try {
        InputStreamResource(s3.getObject(GetObjectRequest.builder().bucket(bucket).key(prefix + name).build()))
    } catch (e: NoSuchKeyException) {
        null
    }
}

@Configuration
@ConditionalOnProperty(prefix = "upload", name = ["storage"], havingValue = "s3")
class S3ImageStorageConfig {

    @Bean
    fun s3ImageStorage(
        @Value("\${upload.s3.bucket}") bucket: String,
        @Value("\${upload.s3.region:ap-northeast-2}") region: String,
        @Value("\${upload.s3.endpoint:}") endpoint: String,
        @Value("\${upload.s3.prefix:uploads/}") prefix: String,
    ): ImageStorage {
        val client = S3Client.builder().region(Region.of(region)).apply {
            // S3 호환 저장소(MinIO 등)는 엔드포인트 지정 + path-style 주소를 쓴다.
            if (endpoint.isNotBlank()) endpointOverride(URI.create(endpoint)).forcePathStyle(true)
        }.build()
        return S3ImageStorage(client, bucket, prefix)
    }
}
