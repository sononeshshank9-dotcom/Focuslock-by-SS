FocusLock - personal app blocker for Android

BUILD IN THE CLOUD (no Android Studio needed)
1. Create a free account at github.com and make a new repository (name: FocusLock).
2. Click "uploading an existing file", then drag EVERYTHING inside this folder
   (including the .github folder) into the page. Click "Commit changes".
   If .github did not upload: Add file > Create new file, type the name
   .github/workflows/build.yml and paste the contents of that file from this folder.
3. Open the "Actions" tab, wait for "Build APK" to turn green (3-6 minutes).
4. Click the finished run, scroll to "Artifacts", download FocusLock-apk (zip),
   unzip it to get app-debug.apk.
5. Send app-debug.apk to your phone (WhatsApp, Telegram, Drive, USB) and install it.
   Allow "install unknown apps" when asked.

FIRST RUN ON THE PHONE
- Open FocusLock, tap "Allow Usage Access" and enable FocusLock.
- Tap "Allow display over other apps" and enable it.
- Pick apps, set minutes, tap Start blocking.
- Phone settings: set FocusLock battery to "Unrestricted" and allow autostart
  (Xiaomi/Oppo/Vivo/Realme), or the system may kill it.
