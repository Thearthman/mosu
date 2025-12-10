# Bug Fix


# UI improvement (implement 3 first)
~~1. the "top bar" which is where the status bar of the phone sits, is now colored grey for some reason. Make it blend in.~~
~~2. Add dark mode.~~
~~3. remove black line below search bar. Also make genre bar sit closer to search bar and song list below, and make the genre buttons slightly smaller.~~
~~4. add most-played view also to support. Support's search page should be able to contain all 4 search methods. We need to rethink about the ordering of these modes, maybe change the UX for switching between 4 modes because one button is too much for 4 modes.~~


# New Feature 
1. Settings page update
    1. implement cache cleaning in settings.
    ~~2. Implement switch in settings that configures whether the played song is filtered by the literal url tag in the search url or the user's most played data. This is because most user without supporter status will not be able to search for their played songs directly through the url. When user don't have supporter, it locks to search by the most played songs directly from user data.~~
    3. Implement sound balancing base on loudness normalization algorithms
    ~~4. Implement search true all songs(unranked, loved, and so on) with url tag `s=any`~~
    5. Add guidance page on how to get get credential in the fill in credential page. Like a help button. I'll write a guidance markdown file on this topic placed in the root folder you'll need to make sure the app will display the markdown file (you can ask me to convert it to pdf or any other format that's best for display and storing in android app). If you can't find the file ask me to make it first.
    ~~6. Implement language dropdown menu. For now, included English, 简体中文 and 繁体中文.~~
    7. Implement language changing feature, support the language in the language menu mentioned in 1.6
2. Add player view 
    ~~1. Add thin `collapsed player view` (like apple music) to the bottom of the page on top of the `navigation bar`. It should have `play/pause`, `next` and `previous` song buttons on the right side of it and a small cover on the left of it and the title of the song in the middle (can be clipped off by the play button if title too long)~~
    ~~2. When area outside of the buttons are click in the `collapsed play view`, it expands upward to fill the screen while the `navigation bar` retract downwards and hides. The cover photo expand, move upwards smoothly following the scroll motion, and fade into the background of the page (at lower brightness and with a subtle blur). The controls are centered and at the lower 50% of the display. It should have `progress bar` that can be dragged, `play/pause`, `next`, `previous`, `single`/`loop`/`random`(they are a single button just like `played`/`all` button) and `mod`(see feature 5 below) buttons. When the area is slide vertically (be careful not to cause unintended motion when sliding the play progress bar) downwards, the playview collaps downward back into the mini player. The cover photo should shrink and becomes solid back again with a smooth motion returning to the left side of the mini player following the motion of the scroll.~~
    ~~3. Add `DT (double time)` and `NC (Night core)` sound effect `mod`. It is activated in player view. double time basically plays the song at 1.5 speed multiplier but keep the pitch of the song unchanged. Night core is double time but pitch is not processed, i.e., it is changed because the song is played at 1.5 times the original speed.~~
    4. Add HT(Half Time) similar to DT that doesn't change pitch.
    ~~5. The shuffle button, when clicked, should cycle (1->2,2->3,3->1,1->2, ...) instead of (1->2,2->3,3->2,2->1, ...)~~
3. Playlist feature
    ~~1. Implement `single`/`loop`/`random`(that loops) feature. As of current, the playlist is the whole library, i.e., loop/random applies to the whole library. The library genre feature should work here to change the playlist.~~
4. Search page updates
    ~~1. When a song is downloaded is clicked in search page, it is played.~~
    ~~2. Implement filter by `favorite`(extracted from user's osu account data) (it should be a part of the `played`/`all` button). So now it alternates between `played`, `all`, `favorite`.`~~
    3. Add an manual query cache refresh action instead of refreshing every 5 mins. The action is: when you are at the top of the song list, dragging the song list downwards reveals a refresh icon and when dragged sufficiently and released, a new query request will be made. Do not remove cached query list every 5 min, store query list forever and when refreshed, compare new query result to existing list and only add new songs to the played query list database.
    ~~4. Sort played query list by data played, which is quite difficult to implement I'll have to think about how to use osu apis and mechanism to achieve this.~~
    5. Merge songs with same title. works similar to the most played page.
5. Library update
    ~~1. Implement library filter. Same as the search genre filter.~~
    ~~2. waiting for loop implementation to make the library genre filter applies to `loop`/`random` playlist.~~
    ~~3. implement a find current song button in library. It should be a button (with a locate icon and transparent background) floating on the bottom left of the song list (still above the miniplayer). When clicked, the song list scrolls to the song that's playing. For now use title for matching. Then briefly make the background of the song that's found blink for one second. It should dissappear when using full player and make sure its button functionality is disabled so it won't cause mistouch in full player. It should not appear when no music is in miniplayer. Also, if there is no matching music, don't move at all, just blink the button itself for a second to indicate error. If the music is already in current view, just blink it. ~~
    4. add search page search bar to library.
6. Add Playlist page 
    1. Add `Playlist` page which you can create album and put music into it. has a create album button on the top right. page view default to all album spreading out. Two albums per row and extends downwards. You can click into albums and the view changes to the album title on top with a play button next to it, with song list below. You can add song here, base on the add button on the top right. or play the album which when using loop/random will only loop the songs in the album.



# Bugs fixed
```
1. When removing song, the red bar persist to exist when the item to be deleted is not the bottom one after deleting it. This could be due to the "fill in" strategy after clearing out the deleted song's space. Also check the red bar disappear condition. Maybe refresh red bar condition after song is deleted.
2. fix `load more` button not shown when exiting search page and coming back quickly. I suggest that we keep the load more data to cache so it's easier when searching intensively. Also check if this caching change fixes this problem, because the button eventually comes back, and I'm suspecting it is the caching check every 5mins that fixes the missing button.
3. lock played filter to search from user's most play song data when user is not supporter feature from new feature No.9. (implement feature 1.2 to fix this bug)
4. beatmaps have cover images in subdirectories (like sb/bg.jpg), but the extractor isn't handling these paths correctly.
1. the mod button still has will change the previous button's position when it is changed. 
2. the miniplay would disppear for a sec before showing again when a new song is clicked.
3. the nav bar would show when song is paused in player view
1. Fix dragging progressbar does not update its position immediately. It waits for the music to play to update the pos.
```