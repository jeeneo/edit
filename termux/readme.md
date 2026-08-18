Run [termux.sh](./termux.sh) inside termux to setup the Android SDK and NDK (clean environment recommended):

```bash
curl -fsL https://github.com/jeeneo/edit/raw/refs/heads/main/termux/termux.sh | bash
```

It will download the requred files and extract automatically

If you would like to pre-download files to install offline (for some reason), download the following files and place them beside the script:
visit https://developer.android.com/studio, scroll down to `Command line tools only` and download `commandlinetools-linux-*_latest.zip`

Then download files:

- [android-build-tools-36.1.0-1.fc44.aarch64.rpm](https://download.copr.fedorainfracloud.org/results/curtisy/android-build-tools/fedora-44-aarch64/Packages/a/android-build-tools-36.1.0-1.fc44.aarch64.rpm)
- [android-ndk-r29-aarch64.7z](https://github.com/lzhiyong/termux-ndk/releases/download/android-ndk/android-ndk-r29-aarch64.7z)

and rename to `commandlinetools.zip`, `android-build-tools.rpm`, `ndk.7z` respectively

or run the following commands on a machine with internet access:

```bash
# Build tools 36.1.0 from Fedora
curl --progress-bar -L -o android-build-tools.rpm "https://download.copr.fedorainfracloud.org/results/curtisy/android-build-tools/fedora-44-aarch64/Packages/a/android-build-tools-36.1.0-1.fc44.aarch64.rpm"

# Android NDK from https://github.com/lzhiyong
curl --progress-bar -L -o ndk.7z https://github.com/lzhiyong/termux-ndk/releases/download/android-ndk/android-ndk-r29-aarch64.tar.xz

# Regular command line tools
curl --progress-bar -L -o commandlinetools.zip "$(curl -s https://developer.android.com/studio | grep -oE "https://dl.google.com/android/repository/commandlinetools-linux-[0-9]+_latest\.zip")"
```

Then copy beside the script, run (`chmod + x`), then execute `./gradlew` as normal

In any other project using the NDK (since in Edit, gradle will automatically update `local.properties` per my `settings.gradle.kts` options), set inside `local.properties`:

```
cmake.dir=/data/data/com.termux/files/usr
```

and remove `cmake.version` declarations from any app-level `gradle.kts` since AGP will complain that versions don't match.
