# Bugs
1. Album photo not the same as it is shown on search page. Fix: Use offical osu api to get the high-res version of the coverphoto for each beatmap.
2. Quick swipe would still delete a song even when the swipe is very small. We should measure the absolute distance instead of the speed
3. Swipe to dismiss should not be activated when the lateral motion is smaller than the horizontal motion, it should only allow motions with a tight angle with the horizontal. Try this first and decide whether we still need to fix bug 3(because it seems like that apple music also has velocity dependent slider but it didn't bothered that much).
4. When Deleting an account in account manager, snap the slider back to the start and call out an warning box with warning and confirmation & decline button. When confirmation is pressed, remove the account from the account manager UI and also physically from storage. 
5. Type things in search view when leaderboard only filter is disabled and clear the text with the clear icon would load a search result that looks like has the filter enabled. Could hint to greater issues in how cached result is used. 
6. Sayobot sometimes cannot download certain beatmaps though they are ranked and wasn't really that new. When download failed, try beatmapset of same name and author. It's like automatically clicking one of the options offered by infoPopup.


# UIUX improvement
1. Rethink on the UI design of profile page, think of sections holding boxes of similar functionality, highlight non-reversible actions.
2. When exiting from search page, and there is text in the search bar. Save the page view and when we comeback restore the view. [important]
3. Make the three rows align vertically along their center @app/src/main/java/com/mosu/app/ui/components/InfoPopup.kt:199-359 


# Pending Refactors(for v1.0)
1. examine the current codebase, down to each file, and propose better naming, code organization so it's intuitive to find functions just by the filename and grouping of functions that serves similar features. Also, Business logic and UI should be separate from each other only UI can be in the UI head folder. 


# New Feature
0. Core Feature
    1. Implement Equalizer 
    2. Implement Activity Heatmap from recent play data (should have week, month, and year view). [nextMajorUpdate]
1. Settings page update
    1. Add guidance page on how to get get credential in the fill in credential page. Like a help button. I'll write a guidance markdown file on this topic placed in the root folder you'll need to make sure the app will display the markdown file (you can ask me to convert it to pdf or any other format that's best for display and storing in android app). If you can't find the file ask me to make it first.
2. Player / Player view updates
    1. Add HT(Half Time) similar to DT that doesn't change pitch.
    2. Implement Rubberband to replace the existing music controller to minimize double time metallic distortion. [MajorUpdate]
    3. Implement sound balancing base on loudness normalization algorithms [nextMajorUpdate]
    4. Implement lyrics service with LRCLIB [nextMajorUpdate]
3. Playlist page updates
    Nothing as of present
4. Search page updates
    1. Long press should trigger vibration when the menu pops up.
5. Library page update
    1. add toggle for artist page, where the song list becomes the artist list. Song with artists of same/similar name will have their work collected at one place. Should have special char and space removed when querying for artist name to make prevent songs not showing up bcs of name typo from beatmap author. When a artist in the artist list is clicked, it should open up a playlist style next stage window that contains a list of songs from the same artist. [nextMajorUpdate]
6. Playlist page update
    1. Implement Playlist management system allowing for rearrangement(placement) of playlists. [nextMajorUpdate]


---


# Implemented Features
0. Core Feature
    1. Add ability to login two accounts and preserve both login info according to bug fix 3.
    2. Implemented automatic region check on app launch to switch to Sayobot API for users in Mainland China.
    3. arguably no need for sayobot api. Because original was fast enough in CN -> fixed by adding manual switch, leaving more room for user.
1. Settings page update
    1. Implement switch in settings that configures whether the played song is filtered by the literal url tag in the search url or the user's most played data. This is because most user without supporter status will not be able to search for their played songs directly through the url. When user don't have supporter, it locks to search by the most played songs directly from user data.
    2. Implement search true all songs(unranked, loved, and so on) with url tag `s=any`
    3. Implement language dropdown menu. For now, included English, 简体中文 and 繁体中文.
2. Player / Player view
    1. Add thin `collapsed player view` (like apple music) to the bottom of the page on top of the `navigation bar`. It should have `play/pause`, `next` and `previous` song buttons on the right side of it and a small cover on the left of it and the title of the song in the middle (can be clipped off by the play button if title too long)
    2. When area outside of the buttons are click in the `collapsed play view`, it expands upward to fill the screen while the `navigation bar` retract downwards and hides. The cover photo expand, move upwards smoothly following the scroll motion, and fade into the background of the page (at lower brightness and with a subtle blur). The controls are centered and at the lower 50% of the display. It should have `progress bar` that can be dragged, `play/pause`, `next`, `previous`, `single`/`loop`/`random`(they are a single button just like `played`/`all` button) and `mod`(see feature 5 below) buttons. When the area is slide vertically (be careful not to cause unintended motion when sliding the play progress bar) downwards, the playview collaps downward back into the mini player. The cover photo should shrink and becomes solid back again with a smooth motion returning to the left side of the mini player following the motion of the scroll.
    3. Add `DT (double time)` and `NC (Night core)` sound effect `mod`. It is activated in player view. double time basically plays the song at 1.5 speed multiplier but keep the pitch of the song unchanged. Night core is double time but pitch is not processed, i.e., it is changed because the song is played at 1.5 times the original speed.
    4. The shuffle button, when clicked, should cycle (1->2,2->3,3->1,1->2, ...) instead of (1->2,2->3,3->2,2->1, ...)
3. Playlist feature
    1. Implement `single`/`loop`/`random`(that loops) feature. As of current, the playlist is the whole library, i.e., loop/random applies to the whole library. The library genre feature should work here to change the playlist.
    2. Long pressing a playlist in playlist page should call out a pop up menu which you can delete or rename the playlist.
    3. Inside a playlist, right swipe a song should remove it from the playlist (with a confirmation popup), left swipe should delete the song from the entire library as usual.
4. Search page updates
    1. When a song is downloaded is clicked in search page, it is played.
    2. Implement filter by `favorite`(extracted from user's osu account data) (it should be a part of the `played`/`all` button). So now it alternates between `played`, `all`, `favorite`.
    3. Add an manual query cache refresh action instead of refreshing every 5 mins. The action is: when you are at the top of the song list, dragging the song list downwards reveals a refresh icon and when dragged sufficiently and released, a new query request will be made. Do not remove cached query list every 5 min, store query list forever and when refreshed, compare new query result to existing list and only add new songs to the played query list database.
    4. Sort played query list by data played, which is quite difficult to implement I'll have to think about how to use osu apis and mechanism to achieve this.
    5. In search page, when displaying the result query, merge songs with same title. for detail on how it works, please reference the merge method in favorite queries. Also we do not need to concern about beatmaps difficulty name here because they are not included in the data returned. You should use the album and song info from the very first song you got with that title. When load more is pressed, it should also skip newly returned songs that already has a song with exact same title present.
    6. Avoid special character injections, and avoid directly adding special character to the http request.
    7. add info popup in search page when clicking beatmaps, should account for the following factors: merged song, not merged song. Account for the fact that beatmaps could be merged and also have multiple difficulties at the same time. The popup should contain difficulties for all merged beatmaps in this situation. Regarding the UIUX of individual song boxes: For each beatmap, it's box simply shows beatmap author,  game mode, star rating range(not displaying individual difficulties now). They can have multiple game mode and corresponding star rating range. When the box is clicked, it brings you to the beatmap's osu website
    8. Change merge algorithm: prioritize to display songs already in the library when merging. Also, when merging, the condition changes to not only should the title match exactly, the author has to match as well.
    9. Change search page info popup to long press to trigger and reassign short press to play.
    10. Add mode selection for recent play filter
    11. Maybe add timestamp to recent played music
    12. Integrated song preview functionality in Search: Clicking an undownloaded song plays a preview from the Osu API (supports Sayobot and official sources).
    13. In infopopup, add ability to download a specific beatmapset. In short, add download button to the right of card of each beatmapset. WWhen the download button is clicked. exit info popup and download this specific beatmapsetId thats been clicked. everything following this point, in term of UI, should be same as before, i.e., there's the progress bar etc. 
5. Library update
    1. Implement library filter. Same as the search genre filter.
    2. waiting for loop implementation to make the library genre filter applies to `loop`/`random` playlist.
    3. implement a find current song button in library. It should be a button (with a locate icon and transparent background) floating on the bottom left of the song list (still above the miniplayer). When clicked, the song list scrolls to the song that's playing. For now use title for matching. Then briefly make the background of the song that's found blink for one second. It should dissappear when using full player and make sure its button functionality is disabled so it won't cause mistouch in full player. It should not appear when no music is in miniplayer. Also, if there is no matching music, don't move at all, just blink the button itself for a second to indicate error. If the music is already in current view, just blink it.
    4. add right swipe to add to playlist action for every song that enter a menu displaying a list of playlist to add to. Should also be able to remove songs from playlists from here.
    1. add search page search bar to library.
6. Add Playlist page
    1. Add `Playlist` page which you can create album and put music into it. has a create album button on the top right. page view default to all album spreading out. Two albums per row and extends downwards. You can click into albums and the view changes to the album title on top with a play button next to it, with song list below. You can add song here, base on the add button on the top right. or play the album which when using loop/random will only loop the songs in the album.

# Implemented UIUX improvements
1. the "top bar" which is where the status bar of the phone sits, is now colored grey for some reason. Make it blend in.
2. Add dark mode.
3. remove black line below search bar. Also make genre bar sit closer to search bar and song list below, and make the genre buttons slightly smaller.
4. add most-played view also to support. Support's search page should be able to contain all 4 search methods. We need to rethink about the ordering of these modes, maybe change the UX for switching between 4 modes because one button is too much for 4 modes.
5. Slider reshape into AM style.
6. Removed Language settings, as this is now following Android system settings.
7. When an individual song within a songpack(beatmapset) is played, the music player should show the difficulty title (individual song title) instead of the songpack(beatmapset) title.
8. Implement language changing feature
9. In Search page, add gamemode labels (images are in icons folder) at the end of the mapper's name's row.
10. Complete account management system overhaul: Removed "main" account hierarchy, implemented bottom sheet account switcher, added long-press credentials management, expired account status indicators with automatic login triggers, and swipe-to-delete functionality for account removal.
11. Enhanced star rating display with precise color-coded backgrounds based on difficulty levels, featuring gradient backgrounds for beatmapsets that emphasize start and end difficulty colors.
12. Commented out Default Search Filter box in settings as it was not useful since the app already stores and uses the last used filter mode.
13. Back swipe gesture in android should not always return to library view but the last view
14. Changed playlist left swipe icon from delete to remove (minus sign).
15. Eliminated margin between bottom track and miniplayer inside playlist for seamless UI.
16. Implemented fixed height (66dp) and text truncation with ellipsis for SwipeableSongList items.
17. Added compact "tiny config" for SelectableSongItem with optimized spacing and background for dialog displays.
18. Added fixed height (56dp) and text ellipsis overflow handling to TrackRowWithSwipe for consistent UI.
19. Implemented long press info popup feature across all song items in Search, Library, and Playlist views with refactored reusable InfoPopup component.
20. Add global player playcount to info popup in search page and order beatmaps this way in the info pop up. Include ranked status in info popup 
21. Applied monospace font with tabular (fixed-width) numbers to star rating displays in InfoPopup for consistent alignment and spacing 
22. Added region and API source indicators to the profile page.
23. Make the indicator for the Region and API Source Indicator Card in the profile page spinning when the region utils is refreshing.
24. Implemented parameterizable left & right swipe icons for `SwipeToDismissSongItem`, using a delete icon in Library and a minus icon in Playlist.
26. Added visual progress feedback for song previews using a horizontal gradient background on the active list item.
27. Implemented preview toggle: clicking a song that is already previewing will now stop the playback.
28. Improved song titles display in lists: implemented consistent truncation with ellipsis for long titles and artists across all views.
29. When everything is set, remove the debug info during downloading in search screen. 
30. Make Manual Api switch more user intuitive.
31. bottom button in the infopopup should not take up that much space @app/src/main/java/com/mosu/app/ui/components/InfoPopup.kt:88-433 replace it with normal dialog to remove the huge buttom padding introduced by alertdialog
32. add a little progress bar in the botton of the miniplayer similar to the style of the full player progress bar but much thinner(similar to download progress bar thickness). It should have similar functionality to the full player progress bar. Also, it should take much all horizontal space.

# Bugs fixed
1. When removing song, the red bar persist to exist when the item to be deleted is not the bottom one after deleting it. This could be due to the "fill in" strategy after clearing out the deleted song's space. Also check the red bar disappear condition. Maybe refresh red bar condition after song is deleted.
2. fix `load more` button not shown when exiting search page and coming back quickly. I suggest that we keep the load more data to cache so it's easier when searching intensively. Also check if this caching change fixes this problem, because the button eventually comes back, and I'm suspecting it is the caching check every 5mins that fixes the missing button.
3. lock played filter to search from user's most play song data when user is not supporter feature from new feature No.9 (implement feature 1.2 to fix this bug)
4. beatmaps have cover images in subdirectories (like sb/bg.jpg), but the extractor isn't handling these paths correctly.
5. the mod button still has will change the previous button's position when it is changed.
6. the miniplay would disppear for a sec before showing again when a new song is clicked.
7. the nav bar would show when song is paused in player view
8. Fix dragging progressbar does not update its position immediately. It waits for the music to play to update the pos.
9. Order of beatmaps displayed in Favorite view is not order to the chronological order that the user favorites the map, which is supposed to be the default ordering of the returned data.
10. Search page info popups should be able to be scrolled.
11. In search page info popup, when there is only one diff, do not show range, instead, show a single star diff.
12. Inside each playlist, there are some songs that have their play button squished probably because of a long title. Remove the Play button because clicking the song itself will lead to play.
13. Recent filter mode doesn't work
14. UI in profile page is bugged because the button being pushed to the right by the text that's too wide. All buttons in profile page should align to the right instead of aligning to the text, and text here should align have a common max width. Update Credential button should have its text centered. Default search view drop down menu button should be much wider because it shows text inside.
15. Cannot download music in favorite page because proper mapping to beatmap link was not made.
16. Song pack in library has wrong background color, it should be very subtle lighter/darker color than the songlist background color depend on the dark/light mode.
17. Should preserve play mode (shuffle, loop single or no loop) even when app is exited
18. Find song doesn't work for individual songs in a songpack.
19. After a period of time the user would be logged out(token expired) is there a way to retain access for a long period of time?
20. implement cache cleaning in settings.
21. Fix token management issues: Implement proactive token refresh using isTokenExpired() check and fix TokenAuthenticator retry logic. Currently users get logged out unexpectedly due to expired tokens not being refreshed properly. Use refresh token to get new tokens.
22. Recent played doesn't work with genre filter. Whats worse is music downloaded from this filter mode does not preserve the genre metadata, it won't be filtered by genre in library either. Never delete recent query, it will be saved for a purpose (year recap). It should also have separators that shows a time stamp(today, three days ago, a week ago, a month ago, 3 months ago, 6 months ago, a year ago, two years ago.)
23. Bug 22 raises my concern on how we deal with metadata after downloads. We need to perform a thorough check on the consistency of storing the downloaded metadata.
24. Fix database destructive migration: Replace fallbackToDestructiveMigration() with proper migration strategies to prevent data loss during app updates. Users currently lose all downloaded beatmaps, playlists, and cached data on schema changes.
25. Searching `artist=miku` in search page crashes the app
26. Do not move downloaded song to top, make them stay where they are default to the position of the returned list
27. Pressing restore when the song already exist will still download the music one more time
28. Navbar and miniplayer design did not account for system navigation bar presence - fixed positioning to maintain consistent 80.dp gap above navbar regardless of system nav bar
29. Fixed SwipeToDismissBox background colors and action handling for both left and right swipes.
30. Restored album collapse functionality when clicking expanded album headers.
31. Fixed extremely slow InfoPopup loading by utilizing nested beatmap data in search results instead of making sequential API calls for each beatmapset, reducing load times from ~25s to ~1s.
32. Optimized supporter status checking to perform only once upon app startup for all logged-in accounts with valid tokens, instead of checking every time search screen loads, significantly improving search screen loading speed.
33. Fixed unranked maps visibility logic: Renamed "Include unranked maps" toggle to "Only songs with Leaderboard" and aligned API search status with the toggle state (enabled = leaderboard only, disabled = s=any).
34. Fixed SayobotApi not working for downloading recent plays in recent play filter in search screen. After download, songs don't show as downloaded in search either in library.
35. Resolved "ID mismatch" bug where downloaded songs wouldn't show as "Downloaded" in certain search filters (Favorite, Recent, etc.) by implementing a unified `BeatmapDownloadService` and Title/Artist metadata matching logic.
36. Fixed "can't add individual songs in an album to playlist" bug by including difficulty name in playlist track storage.
37. Refactored Playlist UI to group tracks by set ID, showing them as `AlbumGroup` only if they were identified as an album (multiple audio files) during download.
38. Added long-press support for album headers to trigger the info popup across Library and Playlist screens.
39. Implemented Room database migration (v14 to v15) to store `isAlbum` flag in `BeatmapEntity` at download time.
40. Fixed "whole album showing in playlist" bug by correcting SQL joins to include `difficultyName`, ensuring only added tracks appear in playlist groups.
41. Resolved `apiSource` inconsistency where UI could show wrong source during initialization due to race conditions.
42. Fixed SearchScreen manual refresh bug where new results weren't immediately visible without navigating away and back.
43. Optimized region detection to avoid redundant background checks and potential state flickering.

# Codebase Maintenances
1. Refactored song list components into specialized UI components: SwipeableSongList, SearchResultList, and SelectableSongList for better separation of concerns.
2. Updated to Material Design 3 and resolved all compilation errors from SwipeToDismissBox migration.
3. Created generic SwipeToDismissWrapper component for reusable swipe-to-dismiss functionality.
4. Refactored AlbumGroup.kt to use SwipeToDismissSongItem.kt components, consolidating swipe logic and improving code maintainability.
5. Unified song list components into a shared "BeatmapSet" architecture with `BeatmapSetList` and `BeatmapSetData`.
6. Refactored `AlbumGroup` into `BeatmapSetExpandableItem` and `SwipeToDismissWrapper` into `BeatmapSetSwipeItem`.
7. Consolidated selection dialogs into `SelectableBeatmapList` and `SelectableBeatmapData`.
8. Updated to Material Design 3 and resolved all compilation errors from SwipeToDismissBox migration.