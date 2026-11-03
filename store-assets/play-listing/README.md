# Play Store listing assets

Everything Play Console's **Main store listing** asks for, already in the formats Play accepts.
The text lives in `fastlane/metadata/android/en-US/` (shared with F-Droid); the images here are
copies of the fastlane and `store-assets/` ones, converted where Play's rules differ.

| Play field | file | why it's a copy |
|---|---|---|
| App icon (512×512, 32-bit PNG with alpha, ≤1 MB) | `icon-512.png` | `store-assets/icon-512.png` has no alpha channel |
| Feature graphic (1024×500, JPEG or 24-bit PNG, no alpha) | `feature-graphic.png` | same as `store-assets/feature-graphic.png` |
| Phone screenshots (2–8; JPEG or 24-bit PNG; no longer than 2:1) | `phone-1.png` … `phone-5.png` | the fastlane ones are 1080×2400 (over 2:1) and have alpha; these are scaled to 2160 px tall, padded to 1080×2160 with the app background, alpha removed |
| 7-inch and 10-inch tablet screenshots | `tablet-1.png` … `tablet-3.png` | the fastlane `tenInchScreenshots` with alpha removed |

Regenerate after new screenshots:

```bash
ffmpeg -i store-assets/icon-512.png -pix_fmt rgba store-assets/play-listing/icon-512.png
ffmpeg -i fastlane/metadata/android/en-US/images/phoneScreenshots/1.png \
  -vf "scale=-1:2160,pad=1080:2160:(ow-iw)/2:0:color=0xFEFBF7" -pix_fmt rgb24 store-assets/play-listing/phone-1.png
ffmpeg -i fastlane/metadata/android/en-US/images/tenInchScreenshots/1.png -pix_fmt rgb24 store-assets/play-listing/tablet-1.png
```
