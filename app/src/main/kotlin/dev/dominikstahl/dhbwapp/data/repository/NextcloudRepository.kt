package dev.dominikstahl.dhbwapp.data.repository

import dev.dominikstahl.dhbwapp.data.local.NextcloudSessionManager
import dev.dominikstahl.dhbwapp.data.local.db.CachedNextcloudFile
import dev.dominikstahl.dhbwapp.data.local.db.NextcloudDao
import dev.dominikstahl.dhbwapp.data.remote.NextcloudClient
import dev.dominikstahl.dhbwapp.data.remote.NextcloudFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class NextcloudRepository(
    private val client: NextcloudClient,
    private val sessionManager: NextcloudSessionManager,
    private val dao: NextcloudDao
) {
    private val cacheDirName = "nextcloud_files"

    fun isLoggedIn(): Boolean {
        return sessionManager.getConfig() != null
    }

    fun getConfig(): NextcloudSessionManager.NextcloudConfig? {
        return sessionManager.getConfig()
    }

    fun saveConfig(config: NextcloudSessionManager.NextcloudConfig) {
        sessionManager.saveConfig(config)
    }

    fun logout() {
        sessionManager.clearConfig()
    }

    suspend fun listFiles(path: String, cacheDir: File): Result<List<NextcloudFile>> {
        val config = sessionManager.getConfig() ?: return Result.failure(Exception("Not logged in"))

        return try {
            val remoteFiles = client.listFiles(
                serverUrl = config.serverUrl,
                username = config.username,
                password = config.appPassword,
                path = path
            )

            if (remoteFiles.isSuccess) {
                val files = remoteFiles.getOrThrow()

                withContext(Dispatchers.IO) {
                    dao.refreshDirectory(path, files.map { ncFile ->
                        CachedNextcloudFile(
                            path = ncFile.path,
                            name = ncFile.name,
                            isDirectory = ncFile.isDirectory,
                            size = ncFile.size,
                            lastModified = ncFile.lastModified,
                            contentType = ncFile.contentType,
                            parentPath = path
                        )
                    })
                }
            }

            remoteFiles
        } catch (e: Exception) {
            val cached = withContext(Dispatchers.IO) {
                dao.getFilesInDirectory(path)
            }
            if (cached.isNotEmpty()) {
                Result.success(cached.map {
                    NextcloudFile(
                        path = it.path,
                        name = it.name,
                        isDirectory = it.isDirectory,
                        size = it.size,
                        lastModified = it.lastModified,
                        contentType = it.contentType
                    )
                })
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun downloadFile(path: String, cacheDir: File): Result<File> {
        val config = sessionManager.getConfig() ?: return Result.failure(Exception("Not logged in"))

        val destFile = resolveCacheFile(path, cacheDir)
        if (destFile.exists()) {
            return Result.success(destFile)
        }

        destFile.parentFile?.mkdirs()
        return client.downloadFile(
            serverUrl = config.serverUrl,
            username = config.username,
            password = config.appPassword,
            path = path,
            destination = destFile
        )
    }

    fun getCachedFile(path: String, cacheDir: File): File? {
        val file = resolveCacheFile(path, cacheDir)
        return if (file.exists()) file else null
    }

    private fun resolveCacheFile(path: String, cacheDir: File): File {
        val cacheRoot = File(cacheDir, cacheDirName)
        val relativePath = path.trimStart('/')
        return File(cacheRoot, relativePath)
    }
}
