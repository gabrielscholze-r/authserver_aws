package br.pucpr.authserver.files

import br.pucpr.authserver.files.FileSystemStorage.Companion.log
import br.pucpr.authserver.users.User
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.web.multipart.MultipartFile

class S3Storage : FileStorage {
    private val s3: AmazonS3 = AmazonS3ClientBuilder.standard()
        .withRegion(Regions.US_EAST_1)
        .withCredentials(EnvironmentVariableCredentialsProvider())
        .build()

    override fun save(
        user: User,
        path: String,
        file: MultipartFile
    ): String {
        val contentType = file.contentType ?: "application/octet-stream"

        val meta = ObjectMetadata().apply {
            this.contentType = contentType
            this.contentLength = file.size
            this.userMetadata["userId"] = "${user.id}"
            this.userMetadata["originalFileName"] = file.originalFilename ?: file.name
        }

        val transferManager = TransferManagerBuilder.standard()
            .withS3Client(s3)
            .build()

        try {
            val upload = transferManager.upload(PUBLIC, path, file.inputStream, meta)
            upload.waitForCompletion()

            log.info("Arquivo salvo com sucesso no S3: s3://$PUBLIC/$path")
            return path
        } catch (e: Exception) {
            log.error("Erro ao enviar arquivo para o S3: ${e.message}", e)
            throw e
        } finally {
            transferManager.shutdownNow(false)
        }
    }


    override fun load(path: String): Resource = InputStreamResource(
        s3
            .getObject(PUBLIC, path.replace("-S-", "/"))
            .objectContent
    )

    override fun urlFor(name: String): String =
        "https://$PUBLIC.s3.amazonaws.com/$name"

    companion object {
        private const val PUBLIC = "gabrielscholze-authserver-public"
    }
}
