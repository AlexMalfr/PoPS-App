# Development instructions

- Preserve unrelated local changes. Build and test an isolated checkout when those changes would affect the deliverable.
- Versioning is automatic in `app/build.gradle.kts`; never bump version numbers manually. The code is the full Git commit count; the name includes the revision, a fingerprint of uncommitted source changes when present, and the build type for debug.
- Distribute builds from committed `main` with full Git history. Keep published history intact so commit counts continue to increase. CI checkouts must use `fetch-depth: 0`.
- Commit each feature separately, then rebuild the APK after the final commit so its version identifies the delivered revision. Check `:app:printAppVersion` and the installed package version.
- Use a full JDK 21 with `jlink` for Android builds. On this Windows workspace Android Studio's `C:/Program Files/Android/Android Studio/jbr` works. If Gradle discovers a stripped VS Code JRE, use `--no-daemon -Dorg.gradle.java.installations.auto-detect=false "-Dorg.gradle.java.installations.paths=C:/Program Files/Android/Android Studio/jbr"`.
- Mark completed tasks in `TODO.md` and document user-visible changes in `README.md`.
