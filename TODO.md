# Bug Fix
1. Album photo not the same as it is shown on search page. Fix: Use offical osu api to get the high-res version of the coverphoto for each beatmap.
2. Fix database destructive migration: Replace fallbackToDestructiveMigration() with proper migration strategies to prevent data loss during app updates. Users currently lose all downloaded beatmaps, playlists, and cached data on schema changes.
3. Fix token management issues: Implement proactive token refresh using isTokenExpired() check and fix TokenAuthenticator retry logic. Currently users get logged out unexpectedly due to expired tokens not being refreshed properly.



# UI improvement (implement 3 first)
1. In Search page, add gamemode labels (images are in icons folder) at the end of the composer's name's line.
2. Add global player playcount to info popup in search page.


# New Feature
1. Settings page update
    1. implement cache cleaning in settings.
    2. Implement sound balancing base on loudness normalization algorithms
    3. Add guidance page on how to get get credential in the fill in credential page. Like a help button. I'll write a guidance markdown file on this topic placed in the root folder you'll need to make sure the app will display the markdown file (you can ask me to convert it to pdf or any other format that's best for display and storing in android app). If you can't find the file ask me to make it first.
2. Player / Player view
    1. Add HT(Half Time) similar to DT that doesn't change pitch.
    2. Implement Rubberband to replace the existing music controller to minimize double time metallic distortion.
3. Playlist feature
    Nothing as of present
4. Search page updates
    1. Long press should trigger vibration when the menu pops up.
    2. Add mode selection for recent play filter
    3. Maybe add timestamp to recent played music
5. Library update
    1. add search page search bar to library.
    2. add toggle for artist page, where the song list becomes the artist list. Song with artists of same/similar name will have their work collected at one place. Should have special char and space removed when querying for artist name to make prevent songs not showing up bcs of name typo from beatmap author. When a artist in the artist list is clicked, it should open up a playlist style next stage window that contains a list of songs from the same artist.
6. Add Playlist page
    1. Implement Playlist management system allowing for rearrangement(placement) of playlist and deletion of playlists.


---


# Implemented Features
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
    1. Long pressing a playlist in playlist page should call out a pop up menu which you can delete or rename the playlist.
    2. Inside a playlist, right swipe a song should remove it from the playlist (with a confirmation popup), left swipe should delete the song from the entire library as usual.
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
5. Library update
    1. Implement library filter. Same as the search genre filter.
    2. waiting for loop implementation to make the library genre filter applies to `loop`/`random` playlist.
    3. implement a find current song button in library. It should be a button (with a locate icon and transparent background) floating on the bottom left of the song list (still above the miniplayer). When clicked, the song list scrolls to the song that's playing. For now use title for matching. Then briefly make the background of the song that's found blink for one second. It should dissappear when using full player and make sure its button functionality is disabled so it won't cause mistouch in full player. It should not appear when no music is in miniplayer. Also, if there is no matching music, don't move at all, just blink the button itself for a second to indicate error. If the music is already in current view, just blink it.
    4. add right swipe to add to playlist action for every song that enter a menu displaying a list of playlist to add to. Should also be able to remove songs from playlists from here.
6. Add Playlist page
    1. Add `Playlist` page which you can create album and put music into it. has a create album button on the top right. page view default to all album spreading out. Two albums per row and extends downwards. You can click into albums and the view changes to the album title on top with a play button next to it, with song list below. You can add song here, base on the add button on the top right. or play the album which when using loop/random will only loop the songs in the album.

# Implemented UI improvements
1. the "top bar" which is where the status bar of the phone sits, is now colored grey for some reason. Make it blend in.
2. Add dark mode.
3. remove black line below search bar. Also make genre bar sit closer to search bar and song list below, and make the genre buttons slightly smaller.
4. add most-played view also to support. Support's search page should be able to contain all 4 search methods. We need to rethink about the ordering of these modes, maybe change the UX for switching between 4 modes because one button is too much for 4 modes.
5. Slider reshape into AM style.
8. Removed Language settings, as this is now following Android system settings.
3. When an individual song within a songpack(beatmapset) is played, the music player should show the difficulty title (individual song title) instead of the songpack(beatmapset) title. 
4. Implement language changing feature

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
2. Should preserve play mode (shuffle, loop single or no loop) even when app is exited
4. Find song doesn't work for individual songs in a songpack. 
3. After a period of time the user would be logged out(token expired) is there a way to retain access for a long period of time?