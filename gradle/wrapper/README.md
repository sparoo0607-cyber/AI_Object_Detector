# Gradle wrapper JAR is missing

This repo ships `gradle-wrapper.properties`, `gradlew`, and `gradlew.bat`, but **not**
`gradle-wrapper.jar` (it is a binary and was not committed). You need it once.

## Option A — Android Studio (easiest)
Open the project in Android Studio (Hedgehog / Iguana or newer). It detects the missing
wrapper and regenerates `gradle-wrapper.jar` automatically on first sync.

## Option B — you already have Gradle on PATH
```bash
gradle wrapper --gradle-version 8.7 --distribution-type bin
```

## Option C — download it directly
```bash
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar
```
Then verify its integrity against the official checksum published at
<https://gradle.org/release-checksums/> (look for the "Wrapper JAR" hash for 8.7)
before trusting it.

After that, `./gradlew assembleDebug` works from the project root.
