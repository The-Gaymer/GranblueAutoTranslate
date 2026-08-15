# Granblue Auto Translate

Prototype Android AccessibilityService for Chrome Android.

## Goal
On `steam.granbluefantasy.com`, detect Chrome's accessible translation control and invoke its accessibility click automatically. The user does not need to tap the translation button.

## Important
This is an experimental prototype. Chrome's translation UI may expose different accessibility labels depending on Chrome version and language. The app includes a small on-device event log to help diagnose this.

## Build
GitHub Actions builds the debug APK from the `Build APK` workflow.
