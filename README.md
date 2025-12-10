# Mosu — osu!-powered offline music player (Android)

## Introduction
Mosu lets you log in with your osu! account, fetch your played/favorite beatmaps, download them as songs, and play them offline with Media3/ExoPlayer. It uses mirrors for `.osz` downloads, extracts audio/cover art, and stores metadata in Room with cached search results for speed.

## Features & Use Cases
- osu! OAuth login with persistent token storage; configurable client ID/secret in-app.
- Search beatmaps with played/all/favorite toggle, genre filters, fuzzy text search, collapsing header, and cursor-based pagination with 5‑minute cache.
- Per-item download progress; already-downloaded items show a checkmark. Click downloaded items (Search or Library) to play immediately.
- Library with albums (expandable by beatmap set), genre filter, and swipe-to-delete (polished red swipe background).
- Media3 playback with background service, fullscreen player, and MiniPlayer that hides when paused.
- Playback mods: No Mod (default), Double Time (1.5x keep pitch), Night Core (1.5x with pitch up); selection persists across tracks until changed.
- Profile page: user info, downloaded count, played-filter mode toggle (URL vs most-played), login/logout, OAuth settings placeholders for Equalizer/Heatmap.
- UI polish: Apple Music-like bottom nav, double-tap Search tab to scroll to top, no divider lines, new app icon.

## Setup Tutorial

1) Prerequisites
- An osu! account, from which you will need to get OAuth app credentials (client ID/secret) from https://osu.ppy.sh/home/account/edit. 

> **IMPORTANT**: How to get your OAuth keys:
> 1. Go to https://osu.ppy.sh/home/account/edit
> 2. Scroll down to find the "OAuth" (开放授权) section
> 3. Click "New OAuth Application"
> 4. Set **Application Callback URL** to exactly: `mosu://callback` (no trailing slash!)
> 5. Give your app a name (e.g., "Mosu")
> 6. Click "Register application"
> 7. Copy your **Client ID** and **Client Secret** (you'll need both) 

2) Configure OAuth (required to log in)
- Launch the app, go to Profile → Configure Credentials.
- Enter your osu! OAuth client ID and secret; they are stored locally via DataStore.
- Login will redirect via `mosu://callback` (already in the manifest).

3) Usage tips
- Search tab: use Played/All/Favorite toggle and genre chips; Load More uses cursor-based pagination. Downloads show progress; checkmarks indicate downloaded.
- Library tab: albums are expandable; swipe left to delete (removes files + DB).
- Player: tap MiniPlayer to expand; use Mod label to pick DT/NC; chosen mod stays active for next songs until changed.
- Profile tab: login/logout, view user info/downloaded count, toggle played-filter mode (URL vs most-played), edit OAuth creds.

4) Notes
- Download source uses mirrors; audio/cover extracted from `.osz` and stored under app files. 
- Room DB version 5 with destructive migrations during development.