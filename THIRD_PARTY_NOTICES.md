# Third-Party Notices

Mosu includes or interoperates with the following third-party works. This file
is provided for attribution and licensing transparency; it is not legal advice.

## osu! default hitsound samples

The bundled default hitsound samples are from
[`ppy/osu-resources`](https://github.com/ppy/osu-resources), commit
[`b86b30a9c1109f612fead25c269f8ad6cae4a1c1`](https://github.com/ppy/osu-resources/tree/b86b30a9c1109f612fead25c269f8ad6cae4a1c1),
under `osu.Game.Resources/Samples/Gameplay/`:

- `{normal,soft,drum}-{hitnormal,hitwhistle,hitfinish,hitclap,slidertick,sliderslide,sliderwhistle}.wav`
- `spinnerspin.wav`
- `spinnerbonus.wav`

Copyright belongs to the osu! resources contributors. These samples are
licensed under the
[Creative Commons Attribution-NonCommercial 4.0 International License (CC BY-NC 4.0)](https://creativecommons.org/licenses/by-nc/4.0/).
Mosu redistributes the audio content without modification; filenames and asset
placement may differ only as required for Android packaging. This use is
non-commercial and does not imply endorsement by the licensors.

## BASS audio library

Mosu uses BASS **2.4.18.3** from
[Un4seen Developments](https://www.un4seen.com/bass.html) for hitsound mixing.
The vendored distribution consists of:

- `app/src/main/java/com/un4seen/bass/BASS.java`
- `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86,x86_64}/libbass.so`

BASS is free for non-commercial use. Commercial use requires a separate licence
from Un4seen Developments. Mosu is distributed free of charge and uses BASS
only for non-commercial playback. The full licence and commercial-licensing
terms are in the BASS distribution's `bass.txt` and at
<https://www.un4seen.com/>.

BASS is copyright Un4seen Developments Ltd. All trademarks remain the property
of their respective owners.

## Takedown and contact

This project is not affiliated with or endorsed by ppy Pty Ltd, osu!, or
Un4seen Developments. If you are a rights holder and believe included material
infringes your rights, please contact the Mosu repository owner through
[GitHub](https://github.com/Thearthman) (or open an issue at
<https://github.com/Thearthman/mosu/issues>) with the affected files, the work
at issue, and the requested action. Valid concerns will be reviewed promptly,
and affected material will be removed or replaced where appropriate.
