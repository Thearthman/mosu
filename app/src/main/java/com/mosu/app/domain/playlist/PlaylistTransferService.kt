package com.mosu.app.domain.playlist

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.PlaylistEntity
import com.mosu.app.data.db.PlaylistTrackEntity
import com.mosu.app.data.repository.OsuRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports/exports a playlist as a portable list of `beatmapSetId`s.
 *
 * `beatmapSetId` is the canonical 1:1 key for an osu! beatmapset, so a plain list of set ids
 * is enough to fully describe a playlist without any of the (non-authoritative) cached
 * metadata. On import, display metadata is re-derived from the local library when the set is
 * already downloaded, or from the osu! API by set id otherwise.
 *
 * File format (utf-8 JSON):
 * ```json
 * {"version":1,"name":"My Playlist","beatmapSetIds":[1234567,2345678]}
 * ```
 * For clipboard convenience the decoder also accepts a bare JSON array (`[1,2,3]`) and, as a
 * last resort, a free-form list of ids pasted as plain text.
 */
object PlaylistTransferService {
    private const val TAG = "PlaylistTransfer"
    private const val DEFAULT_FILE_STEM = "playlist"
    private const val MAX_IMPORT_SETS = 5000

    const val EXPORT_MIME_TYPE = "application/json"
    const val EXPORT_FILE_EXTENSION = "json"

    data class ExportedPlaylist(
        val name: String,
        val setIds: List<Long>,
        val content: String
    )

    /**
     * A set that is referenced by an imported playlist but not present in the local library.
     * The caller is expected to enqueue a download for it.
     */
    data class PendingDownload(
        val setId: Long,
        val title: String,
        val artist: String,
        val creator: String,
        val genreId: Int?,
        val coverUrl: String?
    )

    data class ImportResult(
        val playlistId: Long,
        val playlistName: String,
        val importedSets: Int,
        val importedTracks: Int,
        val skippedSets: Int,
        val pendingDownloads: List<PendingDownload>
    )

    private data class ResolvedSet(
        val title: String,
        val artist: String,
        val difficultyName: String,
        val isDownloaded: Boolean,
        val pendingDownload: PendingDownload? = null
    )

    /**
     * Serializes the playlist with [playlistId] into the portable set-id list format.
     * Returns `null` if the playlist no longer exists.
     */
    suspend fun exportPlaylist(db: AppDatabase, playlistId: Long): ExportedPlaylist? =
        withContext(Dispatchers.IO) {
            val playlist = db.playlistDao().getPlaylistById(playlistId) ?: return@withContext null
            val setIds = db.playlistDao().getPlaylistTrackRefs(playlistId)
                .sortedBy { it.addedAt }
                .map { it.beatmapSetId }
                .distinct()

            ExportedPlaylist(
                name = playlist.name,
                setIds = setIds,
                content = PlaylistFileFormat.encode(playlist.name, setIds)
            )
        }

    /**
     * Creates a new playlist from an exported set-id list and populates it. Set ids that cannot
     * be resolved (not in the local library and not found on the API) are skipped.
     *
     * Returns `null` when the content cannot be parsed or nothing could be imported.
     */
    suspend fun importPlaylist(
        db: AppDatabase,
        repository: OsuRepository,
        content: String,
        defaultName: String
    ): ImportResult? = withContext(Dispatchers.IO) {
        val payload = PlaylistFileFormat.decode(content) ?: return@withContext null
        if (payload.setIds.isEmpty()) return@withContext null

        val name = payload.name?.takeIf { it.isNotBlank() } ?: defaultName
        val playlistId = db.playlistDao().insertPlaylist(PlaylistEntity(name = name))

        var importedSets = 0
        var importedTracks = 0
        var skippedSets = 0
        val pendingDownloads = mutableListOf<PendingDownload>()

        payload.setIds.take(MAX_IMPORT_SETS).forEach { setId ->
            val resolved = runCatching { resolveSet(db, repository, setId) }
                .onFailure { Log.w(TAG, "Unable to resolve beatmapset $setId", it) }
                .getOrNull()

            if (resolved == null) {
                skippedSets++
                return@forEach
            }

            db.playlistDao().addTrack(
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    beatmapSetId = setId,
                    title = resolved.title,
                    artist = resolved.artist,
                    difficultyName = resolved.difficultyName,
                    isDownloaded = resolved.isDownloaded
                )
            )
            resolved.pendingDownload?.let { pendingDownloads.add(it) }
            importedSets++
            importedTracks++
        }

        if (importedSets == 0) {
            // Nothing resolvable — don't leave an empty playlist behind.
            db.playlistDao().deletePlaylist(playlistId)
            return@withContext null
        }

        Log.i(TAG, "Imported playlist '$name': $importedSets sets, $skippedSets skipped")
        ImportResult(
            playlistId = playlistId,
            playlistName = name,
            importedSets = importedSets,
            importedTracks = importedTracks,
            skippedSets = skippedSets,
            pendingDownloads = pendingDownloads
        )
    }

    /** A filesystem-safe `<name>.json` suggestion for the export document picker. */
    fun suggestedFileName(name: String): String {
        val stem = name.trim()
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
            .trim('.', ' ')
            .ifBlank { DEFAULT_FILE_STEM }
            .take(80)
        return "$stem.$EXPORT_FILE_EXTENSION"
    }

    private suspend fun resolveSet(
        db: AppDatabase,
        repository: OsuRepository,
        setId: Long
    ): ResolvedSet? {
        // Prefer the local library: it is already canonical and avoids a network call.
        val local = db.beatmapDao().getTracksForSet(setId)
        val representative = local.firstOrNull()
        if (representative != null) {
            return ResolvedSet(
                title = representative.title,
                artist = representative.artist,
                difficultyName = representative.difficultyName,
                isDownloaded = true
            )
        }

        // Not downloaded: describe the set from the API by set id. We intentionally add a single
        // row per set (matching the "list of beatmapsetid" spec) rather than one row per
        // difficulty.
        val detail = repository.getBeatmapsetDetail(setId)
        val version = detail.beatmaps.firstOrNull()?.version?.takeIf { it.isNotBlank() }
            ?: return null
        return ResolvedSet(
            title = detail.title,
            artist = detail.artist,
            difficultyName = version,
            isDownloaded = false,
            pendingDownload = PendingDownload(
                setId = setId,
                title = detail.title,
                artist = detail.artist,
                creator = detail.creator,
                genreId = detail.genreId,
                coverUrl = detail.covers.listUrl
            )
        )
    }
}

/**
 * Pure encoder/decoder for the portable playlist JSON format. Kept separate from
 * [PlaylistTransferService] so it can be unit tested without a database.
 */
internal object PlaylistFileFormat {
    private const val VERSION = 1
    private const val KEY_VERSION = "version"
    private const val KEY_NAME = "name"
    private const val KEY_SET_IDS = "beatmapSetIds"

    private val gson = Gson()
    private val digitRegex = Regex("""\d+""")

    data class Payload(val name: String?, val setIds: List<Long>)

    fun encode(name: String, setIds: List<Long>): String {
        val json = JsonObject().apply {
            addProperty(KEY_VERSION, VERSION)
            if (name.isNotBlank()) addProperty(KEY_NAME, name)
            add(KEY_SET_IDS, JsonArray().apply { setIds.forEach { add(it) } })
        }
        return gson.toJson(json)
    }

    fun decode(content: String): Payload? {
        val trimmed = content.trim().trimStart('\uFEFF').trim()
        if (trimmed.isEmpty()) return null

        val element = runCatching { gson.fromJson(trimmed, JsonElement::class.java) }.getOrNull()
        when {
            element == null || element.isJsonNull -> Unit // handled by fallback below
            element.isJsonArray -> return Payload(name = null, setIds = element.asJsonArray.toSetIds())
            element.isJsonObject -> {
                val obj = element.asJsonObject
                val name = obj.get(KEY_NAME)
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.takeIf { it.isNotBlank() }
                val idsElement = obj.get(KEY_SET_IDS)
                    ?: obj.entrySet()
                        .map { it.value }
                        .firstOrNull { it.isJsonArray }
                val ids = idsElement?.takeIf { it.isJsonArray }?.asJsonArray?.toSetIds().orEmpty()
                if (ids.isNotEmpty() || name != null) {
                    return Payload(name = name, setIds = ids)
                }
            }
        }

        // Last resort: accept a plain list pasted as free-form text so clipboard import is
        // forgiving even when the user strips the JSON syntax.
        return fallbackDecode(trimmed)
    }

    private fun JsonArray.toSetIds(): List<Long> {
        val ids = LinkedHashSet<Long>()
        forEach { element ->
            val value = element.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: return@forEach
            value.toLongOrNull()?.takeIf { it > 0 }?.let { ids.add(it) }
        }
        return ids.toList()
    }

    private fun fallbackDecode(content: String): Payload? {
        val ids = LinkedHashSet<Long>()
        content.lineSequence().forEach { line ->
            if (line.isBlank() || line.trimStart().startsWith("#")) return@forEach
            digitRegex.findAll(line).forEach { match ->
                match.value.toLongOrNull()?.takeIf { it > 0 }?.let { ids.add(it) }
            }
        }
        return if (ids.isEmpty()) null else Payload(name = null, setIds = ids.toList())
    }
}
