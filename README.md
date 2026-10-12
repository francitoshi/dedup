# dedup
Scan, compare and deduplicate files at high speed also images by likeness.

## Builds

All tasks run with the Gradle wrapper:

```bash
# JAR + runtime dependency distribution
./gradlew build                 # → app/build/distribution/dedup-<version>.jar (+ deps)

# Linux installers (.deb, .rpm)
./gradlew :app:linux-release    # → app/build/installers/linux/

# Windows installers (.exe, .msi)
./gradlew :app:windows-release  # → app/build/installers/windows/

# macOS installers (.dmg, .pkg)
./gradlew :app:mac-release      # → app/build/installers/mac/

# GraalVM native image (requires GraalVM JDK 17)
./gradlew :app:nativeCompile    # → app/build/native/nativeCompile/dedup (dedup.exe on Windows)
```

Individual tasks are also available: `linux-release-deb`, `linux-release-rpm`,
`windows-release-exe`, `windows-release-msi`, `mac-release-dmg`, `mac-release-pkg`.

On each platform only the matching `*-release` task can run (e.g. `.deb`/`.rpm`
build only on Linux). The GitHub Actions workflow builds all of them across the
three platforms automatically.
