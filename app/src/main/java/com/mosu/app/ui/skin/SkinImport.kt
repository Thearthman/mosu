package com.mosu.app.ui.skin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mosu.app.R
import com.mosu.app.domain.skin.CustomSkinImportError
import com.mosu.app.domain.skin.CustomSkinImportResult
import com.mosu.app.domain.skin.CustomSkinLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun importSkinFromUri(
    context: Context,
    uri: Uri,
    customSkinLibrary: CustomSkinLibrary
): CustomSkinImportResult {
    val archiveName = resolveSkinDisplayName(context, uri)
    return customSkinLibrary.import(archiveName) {
        context.contentResolver.openInputStream(uri)
    }
}

internal fun skinImportMessage(
    context: Context,
    result: CustomSkinImportResult
): String = when (result) {
    is CustomSkinImportResult.Imported -> {
        if (result.alreadyExisted) {
            context.getString(
                R.string.profile_skin_already_imported,
                result.skin.name
            )
        } else {
            context.getString(R.string.profile_skin_imported, result.skin.name)
        }
    }

    is CustomSkinImportResult.Failed -> context.getString(
        R.string.profile_skin_import_failed,
        context.getString(skinImportErrorString(result.error))
    )
}

private suspend fun resolveSkinDisplayName(context: Context, uri: Uri): String {
    val displayName = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
    }
    return displayName
        ?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
        ?: "skin.osk"
}

private fun skinImportErrorString(error: CustomSkinImportError): Int = when (error) {
    CustomSkinImportError.INVALID_ARCHIVE -> R.string.profile_skin_error_invalid_archive
    CustomSkinImportError.ARCHIVE_TOO_LARGE -> R.string.profile_skin_error_archive_too_large
    CustomSkinImportError.ENTRY_TOO_LARGE -> R.string.profile_skin_error_entry_too_large
    CustomSkinImportError.UNCOMPRESSED_TOO_LARGE ->
        R.string.profile_skin_error_uncompressed_too_large
    CustomSkinImportError.TOO_MANY_ENTRIES -> R.string.profile_skin_error_too_many_entries
    CustomSkinImportError.UNSAFE_PATH -> R.string.profile_skin_error_unsafe_path
    CustomSkinImportError.DUPLICATE_PATH -> R.string.profile_skin_error_duplicate_path
    CustomSkinImportError.UNSUPPORTED_LAYOUT -> R.string.profile_skin_error_unsupported_layout
    CustomSkinImportError.EMPTY -> R.string.profile_skin_error_empty
    CustomSkinImportError.IO -> R.string.profile_skin_error_io
}
