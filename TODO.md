# TODO

## Active

### Bugs
- [ ] Album photo differs from the Search page image. Use the official osu! API to get the high-res cover image for each beatmap.

### UIUX
- [ ] Add frosted glass fade to most of the components that involves scrolling. 
- [ ] Fix the UI scaling issue.
- [ ] The number displayed inside the download list button is cut off by the button's round border, so when there are two digit of downloads going on, the number gets cut off.

### Features

#### Core
- [ ] Implement Equalizer.
- [ ] [v1.2] Implement Activity Heatmap from recent play data with week, month, and year views.
- [ ] [v1.2] Implement song recommendations based on tags, genre, and related metadata.
- [ ] [v1.3] Implement smart playlist generation based on how long the user stays on songs in random play mode.
- [ ] [v1.2] Implement DJ mixing for smooth transition

#### Settings
- [ ] [v1.4] Add a display toggle for native-language (Unicode) beatmap titles/artists. Both are already stored in the DB (`titleUnicode`/`artistUnicode`); romaji is shown for now as the osu! standard. (Stage 5 of the beatmap-metadata source-of-truth rework.)
- [ ] Add a guidance/help page for getting osu! credentials in the credential setup flow. Display the guidance markdown file from the repo root, or ask for the file if it is missing.

#### Security
- [ ] Re-add account credential (OAuth client id/secret) export/import to the config backup, but encrypt the credentials before writing them. The manifest currently omits credentials entirely; storing them would require an encryption method (e.g. AES-GCM with a user-supplied passphrase) so the exported JSON never contains them in plaintext.

#### Player
- [ ] Add HT/Half Time, similar to DT but without pitch changes.
- [ ] [v2.0] Entire-interface landscape layout (all screens, not just the player).
- [ ] [v2.0] Replace the existing music controller with Rubber Band to reduce double-time metallic distortion.
- [ ] [v1.2] Implement sound balancing based on loudness normalization algorithms.
- [ ] [v1.2] Implement lyrics service with LRCLIB.
- [ ] Implement a current playing playlist that tracks songs to be played and songs already played. In random/shuffle mode, generate a shuffled queue up front instead of picking each next song independently.
- [ ] Implement a current playing playlist display.
- [ ] [v1.2] Add ability to view beatmap comments.

#### Search
- [ ] Trigger vibration when the long-press menu opens.

#### Library
- [ ] [v1.3] Add an artist view toggle. In artist view, group songs by normalized artist names, removing special characters and spaces to reduce typo-related misses. Clicking an artist should open a playlist-style song list for that artist.
- [ ] Make the library genre filter apply to loop/random playlists once current playing playlist logic is in place.

#### Score
- [ ] [v1.2] Add Top Play(top 200) with detailed stats (different from the search/download focused view in search top play filter mode)

## Completed

### Features

#### Core
- [x] Added ability to log in two accounts and preserve both login records.
- [x] Implemented automatic region check on app launch to switch to Sayobot API for users in Mainland China.
- [x] Added manual API/mirror switching after finding the original API was often fast enough in Mainland China.
- [x] [v1.0] Stored downloaded audio/covers in MediaStore and added an automatic manifest restore path for reinstall recovery, including library data, playlists, account credentials, and played history.

#### Settings
- [x] Implemented setting for whether played-song filtering uses the literal search URL tag or the user's most-played data.
- [x] Implemented true all-song search with URL tag `s=any`.
- [x] Implemented language dropdown with English, 简体中文, and 繁體中文.

#### Player
- [x] Added a thin collapsed player view above the navigation bar with cover, title, play/pause, next, and previous controls.
- [x] Added expanded full player view with animated cover transition, hidden nav bar, draggable progress bar, play controls, play mode button, and mod button.
- [x] Added DT and NC sound-effect mods in the player view.
- [x] Changed shuffle button cycling to `1 -> 2 -> 3 -> 1`.
- [x] [v1.1] Added a landscape horizontal player layout (up to a 3:2 ratio): square cover art top-left, title/artist + transport controls top-right, and a full-width progress bar along the bottom.
- [x] [v1.1] Added a wide horizontal player layout (ratio > 3:2, e.g. 16:9): the progress bar sits at the bottom of the text/controls column and the cover band takes the full height.

#### Playlist
- [x] Implemented `single`/`loop`/`random` playback behavior across the active library/playlist scope.
- [x] Added long-press playlist popup menu for delete/rename.
- [x] Added playlist song swipe actions: right swipe removes from playlist with confirmation, left swipe deletes from library.
- [x] Added playlist rename dialog from the long-press edit action.
- [x] Added Playlist page with playlist creation, album grid, playlist detail view, song adding, and playlist play behavior.
- [x] [v1.1] Implemented playlist import/export with a list of beatmapset ids.
- [x] [v1.1] Implemented persisted playlist song rearrangement.

#### Search
- [x] When a downloaded song is clicked in Search, it plays.
- [x] Implemented favorite filter from osu! account data.
- [x] Added manual pull-to-refresh query cache action and stopped automatic 5-minute cache refresh.
- [x] Sorted played query list by date played.
- [x] Merged Search results with the same title.
- [x] Avoided special character injection and direct special-character insertion into HTTP requests.
- [x] Added Search info popup for beatmaps, including merged and unmerged beatmapset cases.
- [x] Updated merge algorithm to prioritize downloaded/library songs and require matching title and artist.
- [x] Changed Search info popup trigger to long press and reassigned short press to play/preview.
- [x] Added mode selection for recent play filter.
- [x] Added timestamps to recent played music.
- [x] Integrated Search preview playback for undownloaded songs using osu! API/Sayobot/official sources.
- [x] Added ability to download a specific beatmapset from InfoPopup.
- [x] Recent play supports all modes.
- [x] [v1.0] Added persistent Search history.
- [x] [v1.1] Added a Settings toggle to make the Recent play filter display every recorded entry as Play History, not just newest played songs.
- [x] [v1.1] Add Top Play(top 200) filter mode.

#### Library
- [x] Implemented library genre filter matching Search genre filter.
- [x] Implemented find-current-song button in Library with scroll/highlight behavior.
- [x] Added right swipe to add/remove songs from playlists.
- [x] Added Search page style search bar to Library.

#### Score
- [x] [v1.1] Added a dedicated score page (besides search/playlist/library/settings) with the panels below.
- [x] Limited the Recently Passed score panel to the latest 30 passed entries.
- [x] Made score page panels collapse/expand when their panel titles are clicked.
- [x] Added an average star-number panel for beatmaps passed in different grades (e.g. "A: 5.8 stars, B: 6.3 stars").
- [x] Added a recently passed beatmaps panel including pp, star rate, and length.
- [x] [v1.1] Added long-press actions to Recent Passed and unified score rows with `BeatmapSetItem`.
- [x] Added an achievements panel: longest map played, fastest (BPM) map played, lowest AR played, highest CS played, and PP record with a "last PP record held x days" info text.
- [x] Refactored the recent score database with a non-destructive migration so it suffices the panels' data requirements.

### UI/UX Improvements
- [x] Blended the top/status bar color into the app UI.
- [x] Added dark mode.
- [x] Removed black line below Search bar and tightened genre bar/list spacing.
- [x] Added most-played view support.
- [x] Reshaped slider into Apple Music style.
- [x] Removed in-app language settings so language follows Android system settings.
- [x] Player shows difficulty title when playing an individual song from a beatmapset.
- [x] Implemented language changing feature.
- [x] Added game mode labels/images to Search result mapper row.
- [x] Overhauled account management: removed main-account hierarchy, added bottom sheet account switcher, long-press credential management, expired-account indicators, automatic login triggers, and swipe-to-delete.
- [x] Enhanced star rating display with precise color-coded backgrounds and gradients for beatmapsets.
- [x] Removed Default Search Filter setting because Search already stores and uses the last filter mode.
- [x] Fixed Android back-swipe behavior so it returns to the previous view instead of always going to Library.
- [x] Changed playlist left-swipe icon from delete to remove/minus.
- [x] Removed margin between bottom track and miniplayer inside playlist.
- [x] Fixed SwipeableSongList item height to 66dp with ellipsis truncation.
- [x] Added compact tiny config for SelectableSongItem.
- [x] Fixed TrackRowWithSwipe item height to 56dp with ellipsis truncation.
- [x] Implemented reusable long-press InfoPopup across Search, Library, and Playlist.
- [x] Added global player play count and ranked status to Search InfoPopup.
- [x] Applied monospace/tabular numbers to InfoPopup star ratings.
- [x] Added region and API source indicators to Profile.
- [x] Added spinning indicator while Region/API Source card refreshes.
- [x] Implemented parameterizable left/right swipe icons for song items.
- [x] Added visual progress feedback for Search song previews.
- [x] Implemented preview toggle: tapping the currently previewing song stops preview.
- [x] Improved song title/artist truncation across lists.
- [x] Removed download debug info from Search screen.
- [x] Made manual API switch more intuitive.
- [x] Reduced InfoPopup bottom-button padding by replacing the large AlertDialog bottom area.
- [x] Added a thin miniplayer progress bar with full-player style behavior.
- [x] Added optional side scrollbar to song lists.
- [x] Saved/restored Search page state through SearchViewModel.
- [x] Added Search swipe actions for downloaded songs: left delete, right add to playlist.
- [x] Stop Search preview when starting real playback.
- [x] Aligned InfoPopup row contents vertically.
- [x] Added snackbar feedback and undo/redo behavior for beatmapset actions.
- [x] Made Library song list consistent with Search list, including a scrolling genre bar.
- [x] Moved finished downloads to the bottom of the download task list.
- [x] Account deletion in the account manager now snaps the slider back and shows a warning confirmation before removing the account from the UI and storage.

### Bug Fixes
- [x] Fixed delete swipe red background persisting after removing a non-bottom item.
- [x] Fixed Search `Load More` button disappearing after quick navigation.
- [x] Fixed cached Search results when clearing text with "Only songs with Leaderboard" disabled.
- [x] Locked played filter to most-played data for non-supporters when URL played search is unavailable.
- [x] Fixed cover extraction for beatmaps with cover images in subdirectories.
- [x] Fixed mod button changing previous button position.
- [x] Fixed miniplayer briefly disappearing when a new song is clicked.
- [x] Fixed nav bar showing when song is paused in player view.
- [x] Fixed progress bar dragging not updating immediately.
- [x] Fixed Favorite view beatmap ordering to preserve returned chronological favorite order.
- [x] Made Search info popup scrollable.
- [x] In Search InfoPopup, show a single star value when there is only one difficulty.
- [x] Removed squished play button inside playlist song rows.
- [x] Fixed recent filter mode.
- [x] Fixed Profile page layout where long text pushed buttons to the right.
- [x] Fixed music download from Favorite page by mapping beatmap links correctly.
- [x] Fixed library song pack background color.
- [x] Preserved play mode across app exits.
- [x] Fixed find-current-song for individual songs inside a song pack.
- [x] Fixed token expiration/logout issues with proactive refresh and TokenAuthenticator retry logic.
- [x] Implemented cache cleaning in Settings.
- [x] Fixed recent played with genre filter and preserved downloaded genre metadata.
- [x] Audited metadata consistency for downloaded beatmaps.
- [x] Replaced destructive migration usage with proper migration strategies where needed.
- [x] Fixed crash when searching `artist=miku`.
- [x] Stopped moving downloaded Search results to the top.
- [x] Prevented restore from downloading an already-existing song again.
- [x] Fixed nav bar/miniplayer positioning around system navigation bar.
- [x] Fixed SwipeToDismissBox background colors and actions.
- [x] Restored album collapse when clicking expanded album headers.
- [x] Optimized InfoPopup loading by using nested beatmap data instead of sequential API calls.
- [x] Optimized supporter status checking to run once at startup.
- [x] Fixed unranked-map visibility by aligning "Only songs with Leaderboard" with API status.
- [x] Fixed Sayobot API downloads for recent plays.
- [x] Fixed downloaded-state ID mismatch across Search filters with unified download matching.
- [x] Fixed adding individual album songs to playlists by including difficulty name.
- [x] Refactored Playlist UI grouping to use AlbumGroup only for real album sets.
- [x] Added long-press album header InfoPopup support across Library and Playlist.
- [x] Added Room migration v14 -> v15 for `isAlbum`.
- [x] Fixed whole-album showing in playlist by joining on difficulty name.
- [x] Fixed API source race that could show the wrong source during initialization.
- [x] Fixed Search manual refresh not immediately showing new results.
- [x] Optimized region detection to avoid redundant checks and flicker.
- [x] Restored highlight indication for finding currently playing song, including songs inside collapsed albums.
- [x] Restored Search view after leaving and returning with text in the Search bar.
- [x] Fixed recent filter mode not auto-loading on app start.
- [x] Fixed screen frame buffer ordering issue.
- [x] [v1.0] Fixed missing mirror downloads by trying earlier alternative beatmapsets with the same title/artist.
- [x] [v1.0] Download actions now resolve same-title/artist beatmapsets by InfoPopup playcount ranking before starting the mirror download.
- [x] Fixed Search favorite genre bar and Library genre bar.
- [x] Deleting a song in Search now updates downloaded state without removing the list item.
- [x] Fixed Favorite page download corruption/missing file ends and downloaded-state updates.
- [x] [v1.0] Downloads continue in the background through a foreground service.
- [x] [v1.0] Download progress updates are passed through the centralized manager consistently.
- [x] [v1.0] Search page has a task-view dropdown for current download tasks.
- [x] Music stops when the audio output changes, for example when headphones disconnect.
- [x] Music and Search previews pause when another app starts media playback.
- [x] Beatmap swipe actions now require distance threshold instead of velocity-only dismissal.
- [x] Beatmap swipe actions now reject diagonal/vertical gestures and allow reversing direction within the same swipe.
- [x] [v1.0] Included the downloaded-song restore list (`preserved_beatmap_set_ids`) in the Mosu export/import manifest so imports populate "Restore Downloads" even when local audio files are gone.
- [x] [v1.0] Fixed Preferred Mirror reliability by using a more durable, persistent geolocation check.

### Codebase Maintenance
- [x] Refactored song list components into specialized UI components.
- [x] Updated to Material Design 3 and resolved SwipeToDismissBox migration compilation errors.
- [x] Created generic SwipeToDismissWrapper for reusable swipe behavior.
- [x] Refactored AlbumGroup.kt to use SwipeToDismissSongItem.kt components.
- [x] Unified song list components into shared `BeatmapSetList`/`BeatmapSetData` architecture.
- [x] Refactored `AlbumGroup` into `BeatmapSetExpandableItem` and `SwipeToDismissWrapper` into `BeatmapSetSwipeItem`.
- [x] Consolidated selection dialogs into `SelectableBeatmapList` and `SelectableBeatmapData`.
- [x] [v1.0] Aligned architecture boundaries by removing direct low-level download-service dependencies from UI screens and moving restore orchestration into a domain service.
- [x] [v1.0] Reworked downloads into a centralized WorkManager-backed manager with persisted Room task state so queued and active downloads can resume after process death.
- [x] Made signing resilient to project moves: keystore path is configurable via `MOSU_RELEASE_STORE_FILE` (auto-detected from `../keystores`, `./keystores`, `~/personal/keystores`), the build no longer hard-fails when the keystore is absent (debug uses the default debug keystore, release is left unsigned with a warning), and a fresh keystore was generated at `~/work/Personal/keystores/mosu.jks`.
- [x] Fixed 16 KB page-size alignment for uncompressed native libraries (`lib/*/libandroidx.graphics.path.so`) by upgrading the Android Gradle Plugin 8.1.1 -> 8.5.1; debug and release APKs now pass `zipalign -c -P 16`.
