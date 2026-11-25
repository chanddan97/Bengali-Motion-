package com.example.core.storage

import android.content.Context
import android.os.StatFs
import java.io.File

class LocalAssetStorage(context: Context) {
    private val rootDir: File = File(context.applicationContext.filesDir, "banglamotion_projects")

    fun ensureProjectDirectory(projectId: String): File {
        val safeId = sanitizeId(projectId)
        val dir = File(rootDir, safeId)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun deleteProjectDirectory(projectId: String): Boolean {
        val safeId = sanitizeId(projectId)
        val dir = File(rootDir, safeId)
        return if (dir.exists()) dir.deleteRecursively() else true
    }

    fun getRootPath(): String {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        return rootDir.absolutePath
    }

    fun calculateUsedBytes(): Long {
        if (!rootDir.exists()) return 0L
        return rootDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    fun calculateAvailableBytes(): Long = runCatching {
        if (!rootDir.exists()) rootDir.mkdirs()
        StatFs(rootDir.absolutePath).availableBytes
    }.getOrDefault(0L)

    private fun sanitizeId(rawId: String): String =
        rawId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
}
