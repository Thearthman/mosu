# TODO

## Active

### Bugs
- [ ] Album photo differs from the Search page image. Use the official osu! API to get the high-res cover image for each beatmap.
- [ ] Quick swipe can still delete a song when the swipe distance is very small. Measure absolute distance instead of only velocity.
- [ ] Swipe-to-dismiss should only trigger for gestures with a tight horizontal angle, not diagonal/vertical motion.
- [x] When deleting an account in account manager, snap the slider back, show a warning confirmation dialog, then remove the account from UI and storage only after confirmation.
- [x] Clearing Search text while "Only songs with Leaderboard" is disabled can load results that look like leaderboard-only mode is enabled. This may indicate a cached-result issue.
- [x] [v1.0] Preferred Mirror doesn't work most of the time. should have used a more durable and persistent way to determine user's geolocation.

### Features

#### Core
- [ ] Implement Equalizer.
- [ ] [v1.2] Implement Activity Heatmap from recent play data with week, month, and year views.
- [ ] [v1.1] Implement song recommendations based on tags, genre, and related metadata.
- [ ] [v1.1] Implement smart playlist generation based on how long the user stays on songs in random play mode.

#### Settings
- [ ] Add a guidance/help page for getting osu! credentials in the credential setup flow. Display the guidance markdown file from the repo root, or ask for the file if it is missing.

#### Player
- [ ] Add HT/Half Time, similar to DT but without pitch changes.
- [ ] [v2.0] Replace the existing music controller with Rubber Band to reduce double-time metallic distortion.
- [ ] [v1.2] Implement sound balancing based on loudness normalization algorithms.
- [ ] [v1.2] Implement lyrics service with LRCLIB.
- [ ] Implement a current playing playlist that tracks songs to be played and songs already played. In random/shuffle mode, generate a shuffled queue up front instead of picking each next song independently.
- [ ] Implement a current playing playlist display.
- [ ] [v1.2] Add ability to view beatmap comments.

#### Playlist
- [ ] [v1.1] Implement playlist rearrangement/placement management.

#### Search
- [ ] Trigger vibration when the long-press menu opens.
- [ ] [v1.1] Add a toggle to make the Recent play filter display every recorded entry as Play History, not just newest played songs.
- [ ] [v1.1] Add Top Play filter mode.

#### Library
- [ ] [v1.1] Add an artist view toggle. In artist view, group songs by normalized artist names, removing special characters and spaces to reduce typo-related misses. Clicking an artist should open a playlist-style song list for that artist.
- [ ] Make the library genre filter apply to loop/random playlists once current playing playlist logic is in place.

## Completed

### Features

#### Core
- [x] Added ability to log in two accounts and preserve both login records.
- [x] Implemented automatic region check on app launch to switch to Sayobot API for users in Mainland China.
- [x] Added manual API/mirror switching after finding the original API was often fast enough in Mainland China.
- [x] [v1.0] Stored downloaded audio/covers in MediaStore and added an automatic manifest restore path for reinstall recovery.

#### Settings
- [x] Implemented setting for whether played-song filtering uses the literal search URL tag or the user's most-played data.
- [x] Implemented true all-song search with URL tag `s=any`.
- [x] Implemented language dropdown with English, 简体中文, and 繁體中文.

#### Player
- [x] Added a thin collapsed player view above the navigation bar with cover, title, play/pause, next, and previous controls.
- [x] Added expanded full player view with animated cover transition, hidden nav bar, draggable progress bar, play controls, play mode button, and mod button.
- [x] Added DT and NC sound-effect mods in the player view.
- [x] Changed shuffle button cycling to `1 -> 2 -> 3 -> 1`.

#### Playlist
- [x] Implemented `single`/`loop`/`random` playback behavior across the active library/playlist scope.
- [x] Added long-press playlist popup menu for delete/rename.
- [x] Added playlist song swipe actions: right swipe removes from playlist with confirmation, left swipe deletes from library.
- [x] Added playlist rename dialog from the long-press edit action.
- [x] Added Playlist page with playlist creation, album grid, playlist detail view, song adding, and playlist play behavior.

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

#### Library
- [x] Implemented library genre filter matching Search genre filter.
- [x] Implemented find-current-song button in Library with scroll/highlight behavior.
- [x] Added right swipe to add/remove songs from playlists.
- [x] Added Search page style search bar to Library.

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

### Bug Fixes
- [x] Fixed delete swipe red background persisting after removing a non-bottom item.
- [x] Fixed Search `Load More` button disappearing after quick navigation.
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
- [x] Fixed Search favorite genre bar and Library genre bar.
- [x] Deleting a song in Search now updates downloaded state without removing the list item.
- [x] Fixed Favorite page download corruption/missing file ends and downloaded-state updates.
- [x] [v1.0] Downloads continue in the background through a foreground service.
- [x] [v1.0] Download progress updates are passed through the centralized manager consistently.
- [x] [v1.0] Search page has a task-view dropdown for current download tasks.
- [x] Music stops when the audio output changes, for example when headphones disconnect.

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
