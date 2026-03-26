# Repository Guidelines

## Project Structure & Module Organization
`app/` is the main Android app module. Kotlin sources live under `app/src/main/java/com/reink`, organized by layer: `data/`, `di/`, `sync/`, and `ui/`. Assets and reader styling live in `app/src/main/assets/`, and Android resources live in `app/src/main/res/`.

`sync-server/` contains the self-hosted Go sync service (`main.go`, `db.go`, `merge.go`, `models.go`). `worker/` contains the Cloudflare relay worker, with source in `worker/src/index.ts`. Root Gradle files (`build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`) manage the Android build.

## Build, Test, and Development Commands
From the repo root:

- `./gradlew assembleDebug` builds the debug APK.
- `./gradlew installDebug` installs the app on a connected device.
- `./gradlew build` runs the full Android build.

For the relay worker:

- `cd worker && pnpm install` installs dependencies.
- `cd worker && pnpm dev` runs the worker locally with Wrangler.
- `cd worker && pnpm deploy` publishes the worker.

For the sync server:

- `cd sync-server && go build -o reink-sync .` builds the runnable server binary.
- `cd sync-server && go test ./...` runs Go tests.

## Coding Style & Naming Conventions
Follow each language’s standard formatter and the surrounding file’s style. For Kotlin, use Android Studio defaults and keep package names under `com.reink.*`; use `PascalCase` for composables, view models, workers, and data classes, and `camelCase` for functions and properties.

Keep feature code close to its layer and screen, for example `ui/settings/SettingsScreen.kt` and `ui/settings/SettingsViewModel.kt`. Use `gofmt` for Go, keep the worker strongly typed, and avoid unrelated reformatting in files without configured linting.

## Testing Guidelines
There are currently no committed Android or worker test suites and no coverage gate. If you touch Android code, run `./gradlew build` and manually verify the affected flow on a device or emulator. If you touch `sync-server/`, add or update Go tests where practical and run `go test ./...`.

## Commit & Pull Request Guidelines
Recent history uses short Conventional Commit prefixes such as `fix:` and `chore:`. Keep subjects imperative and scoped, for example `fix: preserve read-later sync timestamps`.

Pull requests should describe user-visible impact, list manual verification steps, and include screenshots for UI changes. Do not commit secrets: keep signing values in `local.properties`, and pass sync keys through shell environment variables or another untracked local file.
