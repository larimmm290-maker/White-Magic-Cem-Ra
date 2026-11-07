# Phone-only APK build

Acode is a code editor; it cannot compile this Android project. Use GitHub Actions to build the APK in the cloud from your phone.

1. Extract this ZIP on your phone.
2. In Chrome, sign in to GitHub and create a repository named `BlackMagicProCam`.
3. In the repository choose **Add file → Upload files**.
4. Upload all files from the extracted project, including `.github/workflows/build-apk.yml`. If the picker hides `.github`, create that file in GitHub using **Add file → Create new file**, with path `.github/workflows/build-apk.yml`, then paste in the workflow contents.
5. Commit the files to `main`.
6. Open **Actions**. Choose **Build BLACK MAGIC PRO CAM APK** and tap **Run workflow** if it did not start automatically.
7. After a green check appears, open the run and download `BLACK-MAGIC-PRO-CAM-debug-apk` under Artifacts.
8. Extract that artifact ZIP, tap `app-debug.apk`, allow installation from that source if prompted, and install.

A GitHub account and internet are required. This is a debug APK for testing. This starter source does not yet implement true 4K 60fps, actual HDR/HLG10, or high-speed slow motion; camera capabilities vary by phone.
