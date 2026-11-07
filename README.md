# BLACK MAGIC PRO CAM — Android Studio source

## Open
1. Install Android Studio (current stable) and Android SDK 35.
2. Extract this ZIP.
3. Open the `BlackMagicProCam` folder in Android Studio.
4. Let Gradle sync, then connect an Android phone with USB debugging enabled.
5. Press Run.

## What this starter app does
- CameraX live preview
- Back/front camera switching
- Video recording to the app's Movies folder
- UHD/4K preferred quality with CameraX fallback to a supported lower quality
- Basic Camera2 manual ISO and manual focus requests (only if the phone exposes/supports them)
- A cinematic tint overlay on the preview

## Important hardware/implementation limits
This is a working starter project, not a clone of Blackmagic Camera. Camera hardware decides whether UHD, 60 fps, manual ISO/focus and other modes are actually available. The current build does NOT yet encode the cinematic tint into the video, does NOT implement true high-speed slow-motion capture, and does NOT enable true HDR/HLG10 recording. The Slow Motion and HDR buttons currently explain those limitations rather than pretending the modes are active. 4K 60 fps is not guaranteed by selecting UHD; CameraX/device capability must support it.

Videos are saved under the app-specific Movies directory. They are not automatically added to the public Gallery in this starter version.
