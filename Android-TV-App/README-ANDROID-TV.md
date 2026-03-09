# ZetdcDiagrammatics Android TV Version (Planning Folder)

This folder is a **starting point for an Android TV / Android box version** of ZetdcDiagrammatics that could be installed as an APK.

Your current application is:

- Java **Swing / desktop** app
- Packaged as a **fat JAR** for Windows
- Uses AWT/Swing (which **cannot run on Android**)

Android / Android TV instead expects:

- An **Android app project** (Gradle + Android plugin)
- Activities, Views/Compose, Android permissions
- Output as an **APK** or App Bundle

Because of that, an APK **cannot** be generated directly from the existing Swing JAR. A **new Android project** must be created.

## Suggested Next Steps (Android Studio)

1. **Open Android Studio** on your development machine.
2. Create a new project:
   - Template: *Empty Activity*
   - Package name: `com.zetdc.diagrammatics.tv`
   - Minimum SDK: Android TV target (e.g., Android 8.0 or above)
3. In that project:
   - Implement:
     - PDF viewing using an Android‐compatible PDF library.
     - Marker / line / text annotations using Android Views or Compose.
     - Simple login screen and user management (mirroring the desktop app’s roles).
   - Reuse:
     - The **data model ideas** from the desktop app (how markers/lines/text are stored per page).
4. Build the APK from Android Studio:
   - `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`.

## Why this folder exists

This `Android-TV-App` folder is:

- A **placeholder / planning area** in your repository to keep Android‐specific notes and future code.
- It does **not** currently contain a buildable Android project, and no APK is produced in this workspace yet.

When you are ready to invest in an Android TV port, you can either:

- Create the Android project beside this folder and copy its sources here, or
- Treat this folder as documentation/design for an Android developer who will build the APK.

