# Flatpak of the Linux GUI

`io.cyberduck.Cyberduck.yml` wraps the application image of the `linux` module. The image has its own Java runtime, so
the Flatpak build needs neither Java nor the network, only the runtime and the SDK of GNOME.

## Build and run

```sh
# 1. The application image, linux/target/release/Cyberduck
SKIP_SIGN=true mvn install -DskipTests --also-make --projects i18n,profiles,linux

# 2. Runtime and tool
flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
flatpak install --user -y flathub org.gnome.Platform//48 org.gnome.Sdk//48

# 3. Build, install and run
flatpak-builder --user --install --force-clean build-dir setup/flatpak/io.cyberduck.Cyberduck.yml
flatpak run io.cyberduck.Cyberduck --version
```

The workflow `linux-flatpak.yml` does the same on a GitHub runner when it is started by hand and uploads the bundle.

## What is inside the sandbox

* The home folder is shared, because the application uploads and downloads files. Narrow it to
  `--filesystem=xdg-download` and file chooser portals once the application uses the portals.
* The keyring (`org.freedesktop.secrets`) and notifications are reached over D-Bus. The application looks for the tools
  `secret-tool` and `notify-send`, which are not part of the GNOME runtime. Without them passwords are kept in the
  credentials file in `~/.var/app/io.cyberduck.Cyberduck/.duck` and no notifications are shown. Adding `libsecret` and
  `libnotify` as modules, or using the portals, is still to do.
* JavaFX draws with GTK 3 over X11 (`--socket=x11`), which is also what a Wayland session uses through XWayland.
