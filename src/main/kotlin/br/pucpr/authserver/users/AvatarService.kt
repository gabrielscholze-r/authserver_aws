package br.pucpr.authserver.users

import br.pucpr.authserver.exception.UnsupportedMediaTypeException
import br.pucpr.authserver.files.FileStorage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

@Service
class AvatarService(
    @param:Qualifier("fileStorage") val storage: FileStorage
) {
    fun save(user: User, avatar: MultipartFile): String =
        try {
            val contentType = avatar.contentType!!
            val extension = when (contentType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                else -> throw UnsupportedMediaTypeException("jpg", "png")
            }
            val name = "${user.id}/avatar.$extension"
            val path = "$FOLDER/$name"
            storage.save(user, path, avatar)
            name
        } catch (e: Exception) {
            log.error("Error saving avatar for user ${user.id}. Using default.", e)
            DEFAULT_AVATAR
        }

    fun generateAutoAvatar(user: User): String {
        try {
            val gravatarUrl = getGravatarUrl(user.email)
            val avatarBytes = downloadAvatar(gravatarUrl)
                ?: downloadAvatar(getUiAvatarUrl(user.name))
                ?: return DEFAULT_AVATAR

            val extension = "png"
            val name = "${user.id}/auto-avatar.$extension"
            val path = "$FOLDER/$name"

            val multipartFile = object : MultipartFile {
                override fun getName() = "avatar"
                override fun getOriginalFilename() = "avatar.png"
                override fun getContentType() = "image/png"
                override fun isEmpty() = false
                override fun getSize() = avatarBytes.size.toLong()
                override fun getBytes() = avatarBytes
                override fun getInputStream() = ByteArrayInputStream(avatarBytes)
                override fun transferTo(dest: java.io.File) = dest.writeBytes(avatarBytes)
            }

            storage.save(user, path, multipartFile)
            return name
        } catch (e: Exception) {
            log.error("Erro ao gerar avatar automático para ${user.email}", e)
            return DEFAULT_AVATAR
        }
    }

    private fun getGravatarUrl(email: String): String {
        val hash = MessageDigest.getInstance("MD5")
            .digest(email.trim().lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "https://www.gravatar.com/avatar/$hash?d=404"
    }

    private fun getUiAvatarUrl(name: String): String {
        val encodedName = name.replace(" ", "+")
        return "https://ui-avatars.com/api/?name=$encodedName&format=png"
    }

    private fun downloadAvatar(url: String): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000

        return if (conn.responseCode == 200) conn.inputStream.use { it.readAllBytes() }
        else null
    }

    fun load(name: String) = storage.load(name)

    /**
     * Corrigido: evita NPE se path for nulo ou vazio.
     */
    fun urlFor(path: String?): String? {
        if (path.isNullOrBlank() || path == DEFAULT_AVATAR) {
            return storage.urlFor("$FOLDER/$DEFAULT_AVATAR")
        }
        return storage.urlFor("$FOLDER/$path")
    }

    companion object {
        const val FOLDER = "avatars"
        const val DEFAULT_AVATAR = "default.jpg"
        private val log = LoggerFactory.getLogger(AvatarService::class.java)
    }
}
