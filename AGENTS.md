<!-- FOR AI AGENTS - Human readability is a side effect, not a goal -->
<!-- Keep edits factual and evidence-based: verify against the code before writing here. -->

# AGENTS.md

Mosu — an Android (Kotlin/Compose) osu!-library app. It searches osu! beatmapsets,
downloads them from public mirrors, extracts audio/cover, stores them in a Room DB,
and plays them back. Package root: `app/src/main/java/com/mosu/app/`.

**Precedence:** the closest `AGENTS.md` to the files you are changing wins; this root
file holds global + domain defaults.

## Commands (verified)

There is **no** `gradlew` wrapper script in the tree — use the Gradle 8.10 distribution
directly. Offline builds work; network is not required for compile.

| Task | Command | ~Time |
|------|---------|-------|
| Compile | `export JAVA_HOME=~/.jdks/jbr-21.0.11; ~/.gradle/wrapper/dists/gradle-8.10-bin/*/gradle-8.10/bin/gradle :app:compileDebugKotlin --offline` | ~1 min |
| Assemble debug APK | `export JAVA_HOME=~/.jdks/jbr-21.0.11; ~/.gradle/wrapper/dists/gradle-8.10-bin/*/gradle-8.10/bin/gradle :app:assembleDebug --offline` | ~2-4 min |
| Unit tests | `... :app:testDebugUnitTest --offline` | varies |

Toolchain: `namespace com.mosu.app`, `compileSdk 34`, `minSdk 26`, `targetSdk 35`,
`jvmTarget 1.8`. Only test dir present is `app/src/test` (one test file).

## Domain invariants (READ FIRST)

These are the rules the whole codebase is supposed to obey. Violating them is the
root cause of the historical "tracks named `Beatmap <id>`" restore bug.

| Fact | Statement |
|------|-----------|
| **Unique key** | **`beatmapSetId` is the canonical, unambiguous 1:1 key** for an osu! beatmap(set). It is what `Restore Downloads`, playlists, downloads, and backups key on. A `BeatmapEntity.uid` is only a per-difficulty row identity. |
| **Canonical metadata source** | The **downloaded `.osu` file** is the source of truth for track metadata: `title`, `artist`, `difficultyName`, romanized + native (`TitleUnicode`/`ArtistUnicode`), **and set `creator`**. |
| **Genre** | Genre is **NOT** in the `.osu` file. It is set-level and must be fetched from the **API by `beatmapSetId`** (`genre_id`). |
| **No `.osu` ⇒ fail** | The `.osu` file is mandatory for a valid osu! upload. If a download has no parseable `.osu` (or no audio), the download **must fail** — do not register a degraded row. |
| **Mirror consistency** | osu! hashes beatmaps; mirrors must serve the official `.osz` to stay valid. Therefore downloaded `.osu` metadata is effectively identical to the official API's, and using the download as canonical is safe. |
| **Romaji vs native** | Both are stored. **Display uses romaji today**; native-script display is a deferred feature. |
| **Caller metadata is a hint** | Values passed into `DownloadManager.enqueue(...)` / `DownloadTaskEntity` (title/artist/creator/genreId/cover) are **non-authoritative UI hints only**, used for pre-download display and download search. Never persist them as the final row. |

### Canonical data flow

```
download request (beatmapSetId + optional hints)
        │
        ▼
BeatmapDownloader  ── .osz ──►  ZipExtractor.parseOsuFile()   ← SOURCE OF TRUTH (track + creator)
        │                              │
        │                              ▼
        │                      ExtractedTrack (title, artist, difficulty, unicode, creator)
        │
        └── genre: BeatmapDownloadService.resolveGenreId(setId, hint)
                      → hint ?: OsuRepository.getBeatmapsetDetail(setId).genreId   ← API is source for genre
        ▼
BeatmapDownloadService.registerExtractedTracks(...)   ← single writer path
        ▼
TrackService.addTrack(entity, db, context)            ← inserts row + preserved set id + prefs mirror
        ▼
MosuBackupService.exportState(...)                    ← rewrites MediaStore manifest
```

`TrackService.addTrack` is the **only** place that should insert a `BeatmapEntity` from
the download path — it also maintains the preserved list and the backup.

## Database (Room)

`AppDatabase` (`data/db/AppDatabase.kt`), DB name `mosu_database`, **version 20**,
`exportSchema = false`, `.fallbackToDestructiveMigration()` is registered only as a
last resort. Migrations 8→20 are hand-written and guarded (idempotent `PRAGMA table_info`
checks). See that file for the authoritative table definitions.

| Table | Entity | Key(s) | Meaning |
|-------|--------|--------|---------|
| `beatmaps` | `BeatmapEntity` | `uid` PK (auto); **indexed `beatmapSetId`** | One row = one **downloaded difficulty**. A set may have many rows (`isAlbum` for multi-audio sets). |
| `preserved_beatmap_set_ids` | `PreservedBeatmapSetIdEntity` | `beatmapSetId` PK | **The "Restore Downloads" list**: set ids the user ever downloaded. Mirrored into SharedPreferences `preserved_beatmaps` / key `preserved_set_ids`. |
| `playlists` | `PlaylistEntity` | `id` PK | Playlist container; `sortOrder` stores the user-defined grid order. |
| `playlist_tracks` | `PlaylistTrackEntity` | PK `(playlistId, beatmapSetId, difficultyName)` | Membership + denormalized title/artist for undownloaded rows. Keyed on `beatmapSetId` for stability across restores. |
| `recent_plays` | `RecentPlayEntity` | `uid` PK; unique index `scoreId` | Play history pulled from the osu! API. |
| `search_cache` | `SearchCacheEntity` | `queryKey` PK | Serialized `List<BeatmapsetCompact>` + cursor for paginated search. |
| `search_history` | `SearchHistoryEntity` | `query` PK | Recent search queries. |
| `download_tasks` | `DownloadTaskEntity` | `setId` PK | Download queue state + **non-authoritative metadata hints** (see invariants). |

`BeatmapEntity` fields: `uid, beatmapSetId, title, artist, titleUnicode?, artistUnicode?,
creator, difficultyName, audioPath, coverPath, downloadedAt, genreId?, isAlbum`.

## API surface

Base URLs and clients: `data/api/RetrofitClient.kt` (`OSU_BASE_URL = https://osu.ppy.sh/`,
`SAYOBOT_BASE_URL = https://api.sayobot.cn/`; UA `MosuAndroid/1.0 (com.mosu.app)`;
authenticated client injected via `configureAuthentication`).

| Layer | File | Notes |
|-------|------|-------|
| Retrofit interface | `data/api/OsuApi.kt` | Endpoints below. |
| Auth wrapper | `data/api/AuthenticatedOsuApi.kt`, `TokenAuthenticator.kt` | Token refresh. |
| Repository | `data/repository/OsuRepository.kt` | Caching + mapping; prefer this over calling `RetrofitClient.api` directly. |
| Models | `data/api/model/OsuModels.kt`, `SayobotModels.kt` | `BeatmapsetCompact`, `BeatmapsetDetail` (both have `genreId`), `BeatmapDetail`, `SearchResponse`, … |

`OsuApi` endpoints (osu! v2, `osu.ppy.sh`):

| Method | Path | Used for |
|--------|------|----------|
| `getToken` / `refreshToken` | `POST oauth/token` | OAuth (auth-code + refresh). |
| `getMe` | `GET api/v2/me` | Profile / supporter flag. |
| `getUserMostPlayed` | `GET api/v2/users/{id}/beatmapsets/most_played` | "Most played" view + preferred-download resolution. |
| `getUserRecentScores` | `GET api/v2/users/{id}/scores/recent` | Play history. |
| `getUserFavoriteBeatmapsets` | `GET api/v2/users/{id}/beatmapsets/favourite` | Favorites. |
| `searchBeatmapsets` | `GET api/v2/beatmapsets/search` | Search / played / favourites; `q`, `g` (genre), `played`, cursor. |
| `getBeatmapsetDetail` | `GET api/v2/beatmapsets/{beatmapset_id}` | **Genre resolution by set id**, detail view. |

Sayobot (secondary source, `api.sayobot.cn`): search `v2/search`, detail
`v2/beatmapinfo?K={setId}&T=0`. `SayobotModels.kt` maps its `genre` int into
`genreId`; its payload also carries `titleU/artistU` (native) which is currently
**not** mapped.

### Download mirrors

`domain/download/BeatmapDownloader.kt`. Mirrors are tried in order; region can reorder
them (`SettingsManager.preferredMirror`, `utils/RegionUtils.preferredMirrorFor`).

| Mirror | URL pattern |
|--------|-------------|
| Nerinyan | `https://api.nerinyan.moe/d/%s?noVideo=true&noBg=false&NoHitsound=true&NoStoryboard=false` |
| Nerinyan backup | `https://ko2.nerinyan.moe/d/%s?...` |
| Sayobot | `https://dl.sayobot.cn/beatmaps/download/full/%s` (or file-by-file via `beatmaps/files/...`) |
| Catboy/Mino | `https://catboy.best/d/%s` |

## osu! `.osz` / `.osu` file reference (verified against real archives)

An `.osz` is a zip. It must contain at least one `.osu` and the audio file referenced by
`AudioFilename`. `.osu` text sections, in order:
`[General] [Editor] [Metadata] [Difficulty] [Events] [TimingPoints] [Colours] [HitObjects]`.

`[Metadata]` keys observed (all maps): `Title, Artist, Creator, Version, Source,
BeatmapID, BeatmapSetID`. `TitleUnicode` / `ArtistUnicode` are present on modern maps but
**may be absent or blank on older maps**. Fields such as `Creator`/`Title`/`Artist` are
rarely blank. Parsing lives in `domain/download/ZipExtractor.kt` (`parseOsuFile` →
`OsuMetadata` → `ExtractedTrack`).

| Field | Meaning | Notes |
|-------|---------|-------|
| `AudioFilename` (in `[General]`) | Relative audio path | Mandatory; drives track creation. |
| `Title` / `Artist` | Romanized strings | Display values. |
| `TitleUnicode` / `ArtistUnicode` | Native-script strings | May be blank → stored as `null`. `parseOsuFile` checks Unicode keys **before** their romanized counterparts (prefix overlap). |
| `Creator` | **Set host**, not per-difficulty mapper | Verified: guest diffs (e.g. `zoli's Expert`) still carry the host's name. This maps to `BeatmapEntity.creator`. Per-difficulty mappers are only in the API (`user_id`/`owners`), and we do not store them. |
| `Version` | Difficulty name | Maps to `difficultyName`. |
| `Source` | Origin (e.g. `Arcaea`) | Parsed but currently unused. |
| `BeatmapID` / `BeatmapSetID` | Numeric ids | The `.osu` `BeatmapSetID` should equal the set id we downloaded by; verify rather than trust. |
| Background | `[Events]` line `0,0,"bg.jpg",0,0` | Drives cover extraction; heuristic fallback picks any `*bg*`/`*cover*`/first image. |

**Genre is not present anywhere in an `.osz`** (verified by grepping a real archive).
That is precisely why `BeatmapDownloadService.resolveGenreId` exists.

### Genre id enum

The same enum is used by Sayobot and osu! and by the app (`domain/search/BeatmapSearchService.genres`,
`ui/components/GenreFilter.defaultGenres`): `2 Game, 3 Anime, 4 Rock, 5 Pop, 6 Other,
7 Novelty, 9 Hip Hop, 10 Electronic, 11 Metal, 12 Classical, 13 Folk, 14 Jazz`.

## Restore Downloads (RDS)

`domain/restore/RestoreDownloadsService.enqueueMissingPreservedDownloads` iterates the
preserved set ids and enqueues a `DownloadManager` job for each set that has no local
rows. Each job re-downloads from a mirror and re-derives metadata from the `.osu`
(`registerExtractedTracks`). The UI message is phrased as **"Initiated downloading N
songs"** because enqueuing is not completion — the actual work runs in
`BeatmapDownloadWorker` (WorkManager, foreground service).

## Backup / export

`data/media/MosuBackupService.kt` writes a JSON manifest to MediaStore
(`MediaStoreFileService.writeManifest`). `MosuManifest` exports: `accountCredentials`,
`tracks`, `preservedBeatmapSetIds`, `playlists`, `playlistTracks`, `recentPlays`.
`BackupTrack` **deliberately omits `titleUnicode`/`artistUnicode`**: on restore the
download path (RDS) re-derives them from the `.osu`, so the manifest does not need them.
The manifest also includes the **full preserved set id list**, which is what makes
restore-after-reinstall work even when local audio is unreadable.

## File Map

| Path | Purpose |
|------|---------|
| `app/src/main/java/com/mosu/app/` | App source (single module `:app`). |
| `.../data/db/` | Room entities, DAOs, `AppDatabase` (+ migrations). |
| `.../data/api/` | Retrofit `OsuApi`, clients, auth; `model/` are the DTOs. |
| `.../data/repository/OsuRepository.kt` | API access + search caching + mapping. |
| `.../data/media/` | MediaStore file IO + backup manifest. |
| `.../data/services/TrackService.kt` | Single writer/owner of library rows + preserved list. |
| `.../domain/download/` | Mirror downloader, zip/.osu extraction, download service/worker/manager. |
| `.../domain/restore/RestoreDownloadsService.kt` | Enqueue missing preserved sets. |
| `.../domain/search/BeatmapSearchService.kt` | Search merge/dedupe/filter + genre list. |
| `.../domain/render/` | Ruleset-neutral renderer contracts, discovery, matching, cache, and the standard-only renderer implementation. |
| `.../ui/` | Compose screens (`library`, `player`, `playlist`, `profile`, `score`, `search`, `components`, `theme`). Settings live at the **bottom of the Profile tab** (no separate settings screen). |
| `.../player/` | Media3 playback. |
| `.../utils/` | `RegionUtils` (mirror region preference) etc. |

## Renderer architecture (standard implemented; other modes TBD)

Only osu!standard has a parser/compiler/renderer today. Taiko, catch, and mania
are intentionally **not implemented and have no visible UI stubs**; the neutral
contracts exist so adding them does not require reworking Full Player,
discovery, or preference persistence.

| Piece | File | Contract |
|-------|------|----------|
| Ruleset/mode | `domain/render/RenderContracts.kt` | `RenderMode` maps the four osu! modes (`0..3`) to stable app-level modes. |
| Difficulty identity | `domain/render/RenderContracts.kt` | `RenderDifficultyKey(mode, difficultyName)` encodes as `v1:<osuMode>:<difficultyName>`. Decoding splits on the first colon, so names may contain colons. |
| Catalog | `domain/render/RenderContracts.kt` | `RenderCatalog` owns the set/audio selection key and resolves encoded preferences first, then a unique legacy name-only value, then the catalog default. |
| Discovery | `domain/render/RenderCatalogLoader.kt` | Scans headers ruleset-agnostically through `OsuFileHeaderCache`; constructs `RenderDifficulty` values for every known mode. |
| Matching | `domain/render/RenderDifficultyMatcher.kt` | Selects the audio group using all scanned modes, then filters to `RendererRegistry.supportedModes`, deduplicates by `(mode, normalized difficulty name)`, and rejects sets with no renderable map. |
| Registry | `domain/render/RendererRegistry.kt` | Enforces one renderer per mode and exposes failure-safe preparation. `RendererRegistry.default()` currently registers only `OSU`. |
| Standard renderer | `domain/render/osu/OsuStandardBeatmapRenderer.kt` | Adapter that prepares the existing standard timeline/skin and forwards neutral frames to `OsuRenderer`. |

### Renderer API lifecycle

`RendererRegistry.prepare` invokes `BeatmapRenderer.prepare(context, difficulty)`
on `Dispatchers.IO`; renderers may perform file/asset I/O there. Preparation
returns a `PreparedRender` or `null` on failure, and the registry also converts
thrown failures and prepared-mode mismatches into the cover fallback without
swallowing coroutine cancellation. `PreparedRender.draw(canvas, RenderFrame)` is
the frame-loop entry point: it must be pure with respect to `RenderFrame` and
must not perform I/O. `PreparedRender.close()` releases renderer-owned resources
and is called when the media/selection changes or the player leaves composition.

Full Player passes only neutral catalog/difficulty/key, `PreparedRender`, and
the selected renderer's `preferredAspectRatio` into its layouts. The selection
preference remains a string: new writes use `RenderDifficultyKey.encode()`, while
legacy name-only values remain readable when unambiguous.

### Adding a future ruleset

1. Implement that mode's parser/compiler and `BeatmapRenderer` under
   `domain/render/<mode>/`; return a `PreparedRender` whose `mode` matches.
2. Declare the renderer's content aspect ratio with `preferredAspectRatio`.
3. Register the renderer exactly once in `RendererRegistry.default()`.
4. Add mode-specific unit tests for its parser/compiler/renderer.

Discovery, matching, Full Player orchestration, aspect selection, and preference
persistence should need no mode-specific changes. **TBD:** taiko, catch, and
mania parsing, compilation, rendering, and runtime registration.

## Heuristics

| When | Do |
|------|-----|
| Adding/altering track metadata | Read it from the `.osu` (`ZipExtractor`), not from the caller/API. |
| Needing set-level metadata (genre, etc.) | Fetch by `beatmapSetId` from `OsuRepository`; treat failure as non-fatal. |
| Keying anything set-level | Use `beatmapSetId`. |
| Download produced no audio / no parseable `.osu` | Fail the download; never register a placeholder row. |
| Adding a `BeatmapEntity` column | Add a **non-destructive** guarded migration in `AppDatabase` and bump the version. |
| Touching the download path | Keep `TrackService.addTrack` as the only DB writer for library rows. |
| Unsure of a field meaning | Inspect a real `.osz` (`unzip` to a temp dir) before assuming. |

## Boundaries

### Ask first
- Implementing any bug fix or feature — **the user must approve a design before code is written**.
- Any schema change beyond an additive, non-destructive migration.
- Changing which source is canonical for a field.
- Repo-wide refactors.

### Never
- Persist caller-supplied title/artist/creator as the final `BeatmapEntity` values.
- Store `"Beatmap <id>"`-style placeholder metadata.
- Fabricate osu! facts or field meanings — verify against a real archive or the API.
- Destructive DB migrations or deleting migration history.
- Re-introduce a reverted change without explicit approval.

## Codebase State

- In-flight work (uncommitted): Stage 1 (add `titleUnicode`/`artistUnicode` schema + `.osu`
  parsing) and Stage 2 (download path self-sufficient; genre resolved from API by set id)
  are done and compile. **Stage 3** (stop `RestoreDownloadsService` inventing
  `title = "Beatmap <id>"`; make `DownloadTaskEntity` hints-only) and **Stage 4**
  (debug metadata-repair tool exposed as a popup menu at the **bottom of the Profile
  settings page**: refetch by `beatmapSetId`, skip individual failures, toast "X done /
  Y skipped") are **not started**. Stage 5 (native-language display toggle) is deferred.
- `SayobotDetailData` carries `genre`, `titleU`, `artistU` but does not map them today.
- `BeatmapsetDetail` recently gained `genreId` (used by RDS genre resolution).

## Terminology

| Term | Means |
|------|-------|
| Beatmapset / set | A song + all its difficulties. Identified by `beatmapSetId`. |
| Beatmap / diff | One difficulty. Identified by `beatmapId`. |
| osz | Zip archive of a beatmapset. |
| `.osu` | Per-difficulty text file holding metadata + hit objects. |
| RDS | "Restore Downloads" — re-download preserved sets after a data reset/reinstall. |
| Preserved set id | A `beatmapSetId` the user once downloaded, used to drive RDS. |
| Mirror | Third-party host serving `.osz` downloads (Nerinyan/Sayobot/Catboy). |
