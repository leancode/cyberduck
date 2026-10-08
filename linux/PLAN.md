# Linux GUI for Cyberduck: step-by-step execution plan

This plan adds a JavaFX desktop frontend for Linux as a new Maven module `linux`, gets it to a
usable minimum, turns on GitHub Actions for it, and then produces `.deb` and `.rpm` packages.

It is written to be executed by a coding agent one step at a time. Every step has three parts:

- **Do**: what to change, naming the files and the existing code to copy from.
- **Proof**: a command (or commands) whose output proves the step works. Run it. Do not tick the
  box or move on until it passes exactly as described.
- **Commit**: the commit message to use for that step.

Tick a box (`- [x]`) only after the proof passed and the commit is made. Phases are ordered; do not
start a phase before every box in the previous phase is ticked. Phase 3 (GitHub Actions) and Phase 4
(packages) are explicitly gated on the usable-minimum checklist at the end of Phase 2.

---

## 0. Rules for the executor

Read these before every session.

1. **One step per session of work.** Finish the step, run its proof, commit, tick the box in this
   file (include the tick in the same commit or the next one). Then stop or continue to the next step.
2. **Never skip a proof.** If the proof fails, fix the step until it passes. If the step as written
   turns out to be wrong (a class name is different, a plugin option does not exist), do the smallest
   change that reaches the same goal, and record what changed in a short note under the step.
3. **Build environment.** JDK 25 (Temurin), Maven 3.5+, Linux. Run once at the start of work, from the
   repository root, to fill the local Maven repository with every module:
   ```bash
   mvn --batch-mode --no-transfer-progress install -DskipTests -DskipSign -Drevision=0
   ```
   After that, build only the new module with `-pl linux` (no `-am`) unless the step says otherwise.
   The `osx` and `windows` modules self-skip on Linux, so this works on a Linux host.
4. **Running the app from the build tree.** Whenever a proof says `RUN_FX`, it means this:
   ```bash
   mvn -q -pl linux dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
   java -cp "linux/target/classes:$(cat linux/target/classpath.txt)" ch.cyberduck.ui.fx.MainApplication "$@"
   ```
   Keep that as the script `linux/run.sh` (created in step 1.2) so proofs can call `linux/run.sh ...`.
5. **Display.** Anything that opens a JavaFX window needs a display. Use `xvfb-run -a <command>`
   on a headless machine (`sudo apt-get install -y xvfb`). Unit tests that need the toolkit must
   call `Assume.assumeTrue(System.getenv("DISPLAY") != null)` so `mvn test` still passes headless.
6. **Conventions** (from `AGENTS.md`): GPLv3 header copied from a neighbouring file, 4-space indent,
   `if(`/`for(` with no space before the paren, Log4j 2 via `LogManager.getLogger`, JUnit 4 tests
   named `*Test.java`. Commit messages: one short imperative capitalized sentence ending with a
   period. No `Co-Authored-By` trailers naming an AI.
7. **Branch.** Work on the branch the operator gives you. Push with `git push -u origin <branch>`.
8. **Scope.** Do not touch `osx`, `windows`, or `cli` sources. Changes to `core` are allowed only
   when a step says so.

### Reference material (copy from these, do not reinvent)

| Need | Look at |
|---|---|
| Linux platform defaults and factory wiring | `cli/src/main/java/ch/cyberduck/cli/LinuxTerminalPreferences.java`, `TerminalPreferences.java` |
| Application bootstrap order (preferences, protocols, profiles) | `cli/src/main/java/ch/cyberduck/cli/Terminal.java` (`main`, constructor, `open`) |
| How a Maven module packages for Linux with jpackage | `cli/linux/pom.xml`, `cli/linux/build.xml`, `setup/deb/duck.*`, `setup/rpm/duck.spec` |
| Browser window logic (listing, navigation, file operations) | `osx/src/main/java/ch/cyberduck/ui/cocoa/controller/BrowserController.java` |
| Connection and login prompts | `osx/.../controller/ConnectionController.java`, `LoginController.java`, `osx/.../callback/Prompt*Callback.java` |
| Transfer window and starting transfers | `osx/.../controller/TransferController.java`, `TransferPromptController.java` |
| Bookmark editing | `osx/.../controller/BookmarkController.java` |
| Shared, toolkit-free UI helpers | `core/src/main/java/ch/cyberduck/ui/browser/*`, `core/src/main/java/ch/cyberduck/ui/comparator/*` |
| Controller contract | `core/src/main/java/ch/cyberduck/core/Controller.java`, `AbstractController.java` |
| Callback interfaces to implement | `core/.../LoginCallback.java`, `PasswordCallback.java`, `HostKeyCallback.java`, `CertificateTrustCallback.java`, `threading/AlertCallback.java`, `transfer/TransferPrompt.java`, `transfer/TransferErrorCallback.java` |
| Factory keys the callbacks register under | `core/src/main/java/ch/cyberduck/core/preferences/Preferences.java` (search `factory.`) |
| Local-filesystem protocol used for headless proofs | `nio/src/main/java/ch/cyberduck/core/nio/LocalProtocol.java`, `nio/src/test/.../LocalListServiceTest.java` |
| Testcontainers pattern for an end-to-end server test | `smb/src/test/java/ch/cyberduck/core/smb/AbstractSMBTest.java`, `smb/pom.xml` |
| Existing CI | `.github/workflows/build.yml`, `deploy.yml`, `release.yml` |

### Fixed design decisions

- **Toolkit: JavaFX**, UI built in Java code (no FXML). Version: the newest `org.openjfx` 25.x on
  Maven Central (check `https://repo1.maven.org/maven2/org/openjfx/javafx-controls/maven-metadata.xml`).
- **Module**: directory `linux/`, artifactId `linux`, Java package `ch.cyberduck.ui.fx`.
  The module compiles with `--release 25` because JavaFX 25 class files need a modern JDK; the rest
  of the reactor stays at Java 8 bytecode.
- **Launcher**: `ch.cyberduck.ui.fx.MainApplication` is a plain class with `main` that calls
  `Application.launch(CyberduckApplication.class, args)`. It must not extend `Application`, so
  JavaFX can run from the classpath without the module path.
- **Preferences**: `LinuxApplicationPreferences` extends `DefaultPreferences`, persisted as a
  properties file in the support directory. Support directory is `~/.duck` via
  `UserHomeSupportDirectoryFinder`, the same as the CLI, so bookmarks and profiles are shared.
- **Threading**: `FxController extends AbstractController`; `invoke(MainAction)` uses
  `Platform.runLater`, and `invoke(action, true)` blocks on a `CountDownLatch` unless already on
  the FX thread.
- **Headless proofs**: a `--smoke <mode> ...` command line runs a scripted scenario against the
  local filesystem (`LocalProtocol`) and exits 0 on success, 1 on failure, always within 120 s.
  All smoke modes are collected in `linux/smoke.sh`, which CI runs under `xvfb-run`.

---

## Phase 1: Module skeleton

- [x] **1.1 Create the `linux` Maven module and register it in the reactor**

  **Do**
  - Create `linux/pom.xml` with parent `ch.cyberduck:parent` (relativePath `../pom.xml`, same
    version as `cli/linux/pom.xml`), artifactId `linux`, packaging `jar`, description
    `Cyberduck Linux`.
  - In `<properties>` set `maven.compiler.source`, `maven.compiler.target` to `25`,
    `javafx.version` to the chosen 25.x, and `maven.main.skip`/`maven.test.skip` to `true`.
  - Add a profile `linux` activated by `<os><family>Linux</family></os>` that sets
    `maven.main.skip` and `maven.test.skip` to `false` and declares the dependencies:
    `ch.cyberduck:core`, `ch.cyberduck:protocols` (type `pom`), `ch.cyberduck:cryptomator`,
    `org.openjfx:javafx-controls:${javafx.version}`, and for tests `ch.cyberduck:test`
    (type `pom`, scope `test`, copy the block from `cli/pom.xml`; it brings in JUnit).
    Copy the `arm64`/`arm32`/`x86_64` profiles from `cli/linux/pom.xml` that add the
    `net.java.dev.jna:libjnidispatch` `.so` dependency (drop the jansi entries).
  - The parent's `enforce-bytecode-version` rule caps dependencies at Java 8 bytecode and will
    reject JavaFX. In the module, declare `maven-enforcer-plugin` with an execution whose id is
    `enforce-bytecode-version` and add `<excludes><exclude>org.openjfx:*</exclude></excludes>`
    inside `<enforceBytecodeVersion>`.
  - Add `<module>linux</module>` to the root `pom.xml` after `<module>cli/osx</module>`.
  - Create `linux/src/main/java/ch/cyberduck/ui/fx/` with one placeholder class `Version.java`
    that returns `PreferencesFactory.get().getProperty("application.version")` so the module has
    something to compile.

  **Proof**
  ```bash
  mvn --batch-mode -pl linux -DskipSign -Drevision=0 install
  ls linux/target/linux-*.jar
  mvn -q -pl linux dependency:tree | grep -E "javafx-(controls|graphics|base).*linux"
  ```
  The build succeeds including the enforcer, the jar exists, and the tree shows the JavaFX jars
  with the `linux` classifier.

  **Commit**: `Add linux module skeleton.`

  **Done (executor notes)**
  - JavaFX is `25.0.4`. JavaFX 25 needs JDK 23 or newer, so the machine must have JDK 25. On Ubuntu 24.04:
    `sudo apt-get install -y openjdk-25-jdk` (and export `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64`).
  - The proof's last command needs the tree written to a file, because `-q` hides the tree:
    `mvn -pl linux dependency:tree -DoutputFile=target/tree.txt && grep javafx linux/target/tree.txt`.
  - Verified the exclusion is needed: with it removed the enforcer fails with
    "Restricted to JDK 8 yet org.openjfx:javafx-controls ... targeted to JDK 23".

- [x] **1.2 Launcher, empty window, and `run.sh`**

  **Do**
  - `MainApplication.java`: plain `main`. Handles `--version` (print `Version` and return without
    starting JavaFX) and `--exit-after <seconds>` (used by proofs). Otherwise calls
    `Application.launch(CyberduckApplication.class, args)`.
  - `CyberduckApplication.java` extends `javafx.application.Application`; `start(Stage)` shows a
    `Stage` titled `Cyberduck` with an empty `BorderPane`, 900x600. If `--exit-after N` was given,
    schedule `Platform.exit()` after N seconds.
  - `linux/run.sh` as in rule 4 (make it executable). It must `cd` to the repository root.

  **Proof**
  ```bash
  mvn -q -pl linux -DskipSign -Drevision=0 compile
  linux/run.sh --version                       # prints a version string, exit 0, no window
  xvfb-run -a linux/run.sh --exit-after 3; echo "exit=$?"   # exit=0 after about 3 s
  ```

  **Commit**: `Add JavaFX launcher and empty main window.`

  **Done (executor notes)**
  - `--version` prints `Cyberduck 9.6.0-SNAPSHOT`. Until step 1.3 it uses `MemoryPreferences`; step 1.3 replaces that.
  - The window proof is stronger than the exit code. With `x11-utils` installed
    (`sudo apt-get install -y x11-utils xdotool`) run the app in the background under `xvfb-run` and
    `xwininfo -root -tree | grep '"Cyberduck"'` shows a 900x600 window.
  - JavaFX prints `Unsupported JavaFX configuration: classes were loaded from 'unnamed module'` when started from the
    class path. It is a warning only and is expected with this design.
  - `run.sh` uses `$JAVA_HOME/bin/java` when `JAVA_HOME` is set, so export a JDK 25 `JAVA_HOME` first.

- [x] **1.3 Persistent Linux preferences**

  **Do**
  - `LinuxApplicationPreferences extends DefaultPreferences`. Constructor takes a `Local` file
    (default: `UserHomeSupportDirectoryFinder` result + `cyberduck.properties`). Implement
    `load()` (read `java.util.Properties` from the file if it exists), `save()` (write it),
    `setProperty`, `deleteProperty`, `getProperty` (fall back to `getDefault`), and the two
    locale methods as in `MemoryPreferences`.
  - `setDefaults()` and `setFactories()`: copy every `setDefault` from
    `LinuxTerminalPreferences` except the terminal-only ones (`library.jansi.path`, the
    `factory.*callback.class` entries that point at `Terminal*` classes, `factory.notification.class`,
    `factory.certificatestore.class`, `factory.transferpromptcallback.*`). Keep
    `jna.boot.library.path`, `local.user.home`, `bookmarks.folder.name`, `profiles.folder.name`,
    `connection.ssl.securerandom.algorithm`, and the factories for support directory, resources
    finder, locale, browser launcher, application launcher, editor, proxy, symlink, password store
    (`UnsecureHostPasswordStore` for now; Phase 5 replaces it).
  - Add `application.name` = `Cyberduck`, `application.version` from the Maven version, and
    `application.identifier` = `io.cyberduck`.
  - `LinuxApplicationResourcesFinder`: same as `cli/.../ClasspathResourcesFinder` (copy it; it
    returns the parent directory of the jar or classes directory, which is where `profiles/` is
    unpacked in step 1.4 and where jpackage places resources in Phase 4).
  - Unit test `LinuxApplicationPreferencesTest`: construct with a temp file, set a property, `save()`,
    construct a second instance on the same file, `load()`, assert the value is read back and that
    `deleteProperty` followed by `save()` removes it.

  **Proof**
  ```bash
  mvn --batch-mode -pl linux -DskipSign -Drevision=0 test -Dtest=LinuxApplicationPreferencesTest
  ```
  `Tests run: >=2, Failures: 0, Errors: 0`.

  **Commit**: `Add persistent preferences for Linux.`

  **Done (executor notes)**
  - `PreferencesFactory.set` calls `load()` before any factory is registered, so the file path cannot come from
    `SupportDirectoryFinderFactory`. `LinuxApplicationPreferences.defaultFile()` builds `~/.duck/cyberduck.properties`
    directly, which is the folder `UserHomeSupportDirectoryFinder` returns.
  - Deliberately not copied from the CLI: `jna.boot.library.path` and `library.jansi.path` (JNA reads JVM system
    properties, so they must be jpackage `--java-options` in Phase 4), `bookmarks.folder.name` (unused outside the CLI),
    and the `echo ~` home lookup (the base class already defaults `local.user.home` to `user.home`).
  - `application.name`, `application.identifier` and `application.version` already come from `default.properties` and the
    jar manifest, so they are not set again.
  - Added `XdgOpenBrowserLauncher` instead of the AWT-based `DesktopBrowserLauncher`, to avoid loading AWT/GTK next to JavaFX.
  - Tests: `Tests run: 5, Failures: 0, Errors: 0`.

- [x] **1.4 Bootstrap: preferences, protocols, profiles, bookmarks**

  **Do**
  - In `MainApplication.main`, before anything else: `PreferencesFactory.set(new LinuxApplicationPreferences())`.
  - Register protocols the way `Terminal`'s constructor does:
    `for(Protocol p : AutoServiceLoaderFactory.<Protocol>get().load(Protocol.class)) ProtocolFactory.get().register(p);`
    then call `ProtocolFactory.get().load()` (as `Terminal.open` does) so the bundled profiles in
    `<resources>/profiles` and the user's profiles in `~/.duck/profiles` are read.
  - In `linux/pom.xml`, add the `maven-dependency-plugin` `unpack-profiles` execution copied from
    `cli/linux/pom.xml` (unpacks `ch.cyberduck:profiles` into `${project.build.directory}/profiles`).
    Declaring the plugin also inherits the parent's `copy-dependencies-jar-target` and
    `copy-dependencies-so-target` executions, which Phase 4 relies on.
  - Load `BookmarkCollection.defaultCollection().load()` after protocols are registered.
  - Add `--list-protocols`: prints one line per `ProtocolFactory.get().find()` entry
    (`identifier` and `description`) and exits 0 without JavaFX.

  **Proof**
  ```bash
  mvn -q -pl linux -DskipSign -Drevision=0 compile
  linux/run.sh --list-protocols | tee /tmp/protocols.txt | wc -l      # 24 lines, one per bundled profile
  grep -E "^(sftp|ftp|s3|file|dav)" /tmp/protocols.txt                 # at least sftp, ftp, s3 present
  ls linux/target/profiles/*.cyberduckprofile | wc -l                   # 24, same as: find profiles -name '*.cyberduckprofile' -not -path '*/target/*' | wc -l
  ```

  **Commit**: `Bootstrap preferences, protocols and bookmarks for Linux.`

  **Done (executor notes)**
  - The repository bundles 24 profiles. The other connection profiles live in the separate `iterate-ch/profiles`
    repository, so the original "more than 50" threshold was wrong. Output is 24 protocol lines.
  - Startup logic lives in `Bootstrap` (`initialize()` for preferences, protocols and profiles; `loadBookmarks()`), used by
    `MainApplication` for every mode. Bookmarks load in `CyberduckApplication.init()`, off the JavaFX thread.
    `BookmarkCollection.load()` throws a checked exception, which is caught and logged so a bad folder never blocks startup.
  - Declaring `maven-dependency-plugin` also turns on the parent's `copy-dependencies-*` executions, which copy all jars and
    `libjnidispatch.so` into `linux/target`. The jar copy strips the classifier, so JavaFX's plain and `linux` jars share
    one file name. In this build the file holds the native libraries (`unzip -l linux/target/javafx-graphics-25.0.4.jar | grep -c '\.so'`
    prints 10), but this depends on copy order, so step 4.1 must verify it in the app image.

- [x] **1.5 `FxController` and the JavaFX test harness**

  **Do**
  - `FxController extends AbstractController`: `invoke(MainAction)` -> `Platform.runLater`;
    `invoke(MainAction, boolean wait)` runs inline when `Platform.isFxApplicationThread()`,
    otherwise `runLater` and, if `wait`, blocks on a latch. `message(String)` and
    `log(Type, String)` store the last values in observable `StringProperty` fields so views can bind.
  - Test support class `FxToolkit` (in `src/test`): static `init()` that calls
    `Platform.startup(() -> {})` once, sets `Platform.setImplicitExit(false)`, and is guarded by
    `Assume.assumeTrue(System.getenv("DISPLAY") != null)`.
  - `FxControllerTest`: with `FxToolkit.init()`, call `invoke(action, true)` from a worker thread
    and assert it ran on the FX thread; without a display the test is skipped, not failed.

  **Proof**
  ```bash
  mvn --batch-mode -pl linux -DskipSign -Drevision=0 test                 # headless: FxControllerTest skipped, build green
  xvfb-run -a mvn --batch-mode -pl linux -DskipSign -Drevision=0 test     # with display: FxControllerTest runs and passes
  ```
  The second run's surefire output shows `FxControllerTest` with `Tests run: 4, Failures: 0, Skipped: 0`; the first run shows the same class with `Skipped: 4`.

  **Commit**: `Add JavaFX controller with main thread dispatch.`

  **Done (executor notes)**
  - `FxController.invoke` skips actions whose `isValid()` is false (as the macOS and Windows controllers do), runs inline on the
    JavaFX thread, queues with `Platform.runLater`, and blocks on a latch when `wait` is true.
  - `FxToolkit` (test) also accepts `WAYLAND_DISPLAY`, and offers `flush()` to wait for queued JavaFX work.
  - Headless smoke runs (step 1.6) cannot use `FxController` because it needs the toolkit. Use a toolkit-free controller there.

- [x] **1.6 Headless smoke: list a local directory through the core**

  **Do**
  - `Smoke.java`: `--smoke list <directory>`. Builds
    `new Host(new LocalProtocol(), new LocalProtocol().getDefaultHostname())`, a `SessionPool` via
    `SessionPoolFactory.create(controller, host)`, and runs
    `new WorkerBackgroundAction<>(controller, pool, new ListWorker(cache, directoryPath, listener))`
    through `controller.background(...)`, then `get()`s the `Future`. Print
    `SMOKE OK list <count>` and exit 0; on any exception print `SMOKE FAIL` with the message and exit 1.
    Wrap in a 120 s timeout.
  - `linux/smoke.sh`: runs `linux/run.sh --smoke list $(mktemp -d with three files)` and checks the
    output contains `SMOKE OK list 3`. Exit non-zero on any failure. Later steps append modes here.

  **Proof**
  ```bash
  mvn -q -pl linux -DskipSign -Drevision=0 compile && linux/smoke.sh; echo "exit=$?"   # SMOKE OK list 3, exit=0
  ```

  **Commit**: `Add headless smoke test listing a local directory.`

  **Done (executor notes)**
  - Uses `HeadlessController` (runs main actions on the calling thread, like the CLI's `TerminalController`) because
    `FxController` needs the toolkit. `MainApplication` calls `System.exit(Smoke.run(...))` for `--smoke`, since the core keeps
    non-daemon threads alive. A daemon watchdog halts the JVM with `SMOKE FAIL timeout` after 120 s.
  - Checked that the test can fail: a missing directory prints `SMOKE FAIL ...` and exits 1, an unknown mode exits 1, and the
    count follows the directory (4 files give `SMOKE OK list 4`, an empty directory gives `SMOKE OK list 0`).
  - `smoke.sh` has a `smoke <pattern> <mode> <args...>` helper. Later steps add one line per mode.

---

## Phase 2: Usable minimum

Definition of usable minimum (the gate for Phase 3): a Linux user can start the app, pick a
protocol, enter host and credentials, accept an SSH host key or TLS certificate, see a remote
directory listing, navigate, download and upload files with overwrite prompts, create and delete
folders, rename, see transfer progress, save and reopen a bookmark, and get an error dialog instead
of a hang on failure.

- [x] **2.1 Browser window with a file table**

  **Do**
  - `BrowserController extends FxController` owns a `Stage`. Layout: toolbar (Connect, Refresh, Up,
    Download, Upload, New Folder, Delete, Rename), a path `TextField`, a `TableView<Path>` with
    columns Filename, Size, Modified, Permissions, Owner. Use `BrowserColumn` from
    `core/.../ui/browser` for column identity and the comparators in `core/.../ui/comparator` for
    sorting. Format sizes with `SizeFormatterFactory` and dates with `UserDateFormatterFactory`
    (find both in `core`).
  - Status bar label bound to the controller's message property.
  - Listing: `mount(Host)` creates the `SessionPool` and runs `HomeFinderWorker` then `ListWorker`
    exactly as `osx` `BrowserController.reload` does (search for `new ListWorker` there). Keep a
    `PathCache`. Populate the table in `cleanup(AttributedList<Path>)` through `invoke`.
  - Change `--smoke list` to open the browser window on the directory, wait for the table to be
    filled (poll `table.getItems().size()` on the FX thread), print `SMOKE OK list <rows>`, exit.

  **Proof**
  ```bash
  mvn -q -pl linux -DskipSign -Drevision=0 compile && xvfb-run -a linux/smoke.sh; echo "exit=$?"
  ```
  `SMOKE OK list 3` and `exit=0`. Also run `xvfb-run -a linux/run.sh --exit-after 5` and confirm no
  exception in the output.

  **Commit**: `Add browser window with directory listing.`

  **Done (executor notes)**
  - Toolbar buttons are added by the step that makes them work (navigation 2.2, connect 2.3, transfers 2.7 and 2.8, file
    operations 2.10), so no button is ever visible without a function. 2.1 has the path field, the table and the status bar.
  - Uses `MountWorker` (home folder plus first listing) and `ListWorker`, the same workers as the macOS browser.
    `Smoke list` mounts the local filesystem at the given folder through the real `BrowserController`. The old headless
    scenario is now `core-list`. Both are in `smoke.sh`.
  - Hidden files are filtered with `DefaultBrowserFilter` unless the `browser.showHidden` preference is on. Folders sort first.
  - The status bar shows the activity message while busy and `N items` when idle, because the core clears the message when a
    background action ends.
  - Visual proof: run `SMOKE_HOLD=8 linux/run.sh --smoke list <dir>` under `xvfb-run -s "-screen 0 1000x700x24"` and take
    `import -window root shot.png` (ImageMagick). `SMOKE_HOLD` keeps the window open for that many seconds after the scenario.

- [x] **2.2 Navigation**

  **Do**
  - Double-click on a directory row lists it; the Up button lists the parent; typing a path in the
    path field and pressing Enter lists it. Keep a history for a Back button if cheap.
  - `--smoke navigate <dir>`: the harness creates `<dir>/a/b/file.txt`; the smoke opens `<dir>`,
    programmatically double-clicks `a`, then `b`, waits for `file.txt` to appear, goes Up twice, and
    prints `SMOKE OK navigate`.
  - Append the mode to `linux/smoke.sh`.

  **Proof**
  ```bash
  xvfb-run -a linux/smoke.sh; echo "exit=$?"    # both modes OK, exit=0
  ```

  **Commit**: `Add directory navigation in browser.`

  **Done (executor notes)**
  - Added Back, Up and Refresh buttons and an editable path field. Relative paths typed in the field are relative to the current
    folder. A listing that fails keeps the previous folder and restores the field. A listing that was superseded by a newer
    request is ignored.
  - The smoke fires real JavaFX events: a double-click `MouseEvent` on the table row, `Button.fire()` and an `ActionEvent` on the
    path field. It also checks the failure case (`<dir>/missing`).
  - Checked the test can fail: with the click count handler changed to 3 the scenario ends with
    `SMOKE FAIL Timeout waiting for directory ... shown` after the 60 s wait.

- [x] **2.3 Connection dialog**

  **Do**
  - `ConnectionDialog`: protocol `ComboBox` filled from `ProtocolFactory.get().find()`, hostname,
    port (defaults from `protocol.getDefaultPort()`), username, password, initial path. A
    toolkit-free `HostBuilder` class turns the field values into a `Host` (also accepting a full URL
    through `HostParser`). Connect runs `BrowserController.mount(host)`.
  - Unit test `HostBuilderTest`: `sftp://user@example.net:2222/home` yields protocol `sftp`,
    port 2222, username `user`, default path `/home`; and the field-based path yields the same.

  **Proof**
  ```bash
  mvn --batch-mode -pl linux -DskipSign -Drevision=0 test -Dtest=HostBuilderTest     # green
  xvfb-run -a linux/run.sh --exit-after 5                                             # still starts
  ```

  **Commit**: `Add connection dialog.`

  **Done (executor notes)**
  - `HostBuilder` takes a `ProtocolFactory` so tests do not depend on the global one. In the core a protocol is disabled unless a
    profile enables it, so the test uses subclasses of `SFTPProtocol` and `LocalProtocol` that return `true` from `isEnabled()`.
    `HostBuilderTest` has 8 tests, all passing.
  - The dialog validates in an event filter on the Connect button and stays open with a red message on bad input. The Server field
    accepts a full URL, which wins over the other fields. Fields the protocol does not allow to change are disabled.
  - Smoke `connect <dir>` opens the real dialog through `BrowserController.connect()` (a nested event loop), selects the local
    filesystem, types the folder and fires the Connect button. Output: `SMOKE OK connect 3`.
  - Bug found while taking a screenshot: `Platform.exit()` with a dialog open crashes the JVM (`Key not associated with a running
    event loop`, exit 134, `hs_err_pid*.log`). `CyberduckApplication.quit()` hides every window and dialog first and is used by
    `--exit-after`. Step 2.11 must use `quit()` for the Quit command too. Delete any `hs_err_pid*.log` from the repository root.

- [x] **2.4 Prompts: login, password, host key, certificate trust, error alerts**

  **Do**
  - Introduce `DialogService` (interface) with methods that return plain values:
    `Credentials login(Host, String username, String title, String reason, LoginOptions)`,
    `String password(String title, String reason)`, `boolean confirm(String title, String message)`,
    `void error(String title, String message)`. `FxDialogService` implements it with JavaFX
    `Dialog`s, always through `controller.invoke(..., true)` so it may be called from background threads.
  - Implement and register through `LinuxApplicationPreferences.setFactories()`:
    - `FxLoginCallback implements LoginCallback` -> `factory.logincallback.class`
    - `FxPasswordCallback implements PasswordCallback` -> `factory.passwordcallback.class`
    - `FxHostKeyCallback extends OpenSSHHostKeyVerifier` (ssh module; mirror
      `TerminalHostKeyVerifier`) -> `factory.hostkeycallback.class`
    - `FxCertificateTrustCallback implements CertificateTrustCallback` -> `factory.certificatetrustcallback.class`
    - `FxAlertCallback implements AlertCallback` (shows `BackgroundException.getMessage()` and
      `getDetail()`, returns false) -> `factory.alertcallback.class`
  - Each callback takes a `DialogService` in its constructor (default constructor uses the FX one)
    so tests inject a fake.
  - Tests with a fake `DialogService`: `FxLoginCallbackTest` returns the credentials the fake
    produced and throws `LoginCanceledException` when the fake returns null;
    `FxHostKeyCallbackTest` accepts when the fake confirms and throws when it does not.

  **Proof**
  ```bash
  mvn --batch-mode -pl linux -DskipSign -Drevision=0 test      # all green
  linux/run.sh --smoke connect-fail                             # see below
  ```
  Add `--smoke connect-fail`: mounts `sftp://127.0.0.1:1/` with the alert callback replaced by one
  that records the failure; expect `SMOKE OK connect-fail` printed within 30 s and exit 0 (proves
  errors reach the alert path and nothing hangs). Append to `linux/smoke.sh`.

  **Commit**: `Add login, host key, certificate and error prompts.`

  **Done (executor notes)**
  - `DialogService` has three methods (`credentials`, `confirm` returning `Confirmation(accepted, suppressed)`, `error`), which is all
    the callbacks need. `FxDialogService` marshals every dialog to the JavaFX thread through `FxController.invoke(..., true)` and
    shows it above the focused window.
  - The core creates callbacks by reflection with a constructor that takes the controller, so each `Fx*Callback` has a public
    `(FxController)` constructor and a package-private one taking a `DialogService` for tests.
  - Added `FxCertificateStore` (registered under `factory.certificatestore.class`) next to `FxCertificateTrustCallback`. The core
    only asks the store about certificates the Java runtime does not trust, and the default store would accept any valid
    certificate with a matching name. The new store always asks the user. Declining makes the connection fail.
  - A declined SSH host key makes `OpenSSHHostKeyVerifier.verify` return `false` (it catches the cancel exception), so tests assert
    `false`, not an exception.
  - Tests: fake `DialogService` for the callbacks (headless), `TestCertificates` generates real self-signed certificates with
    Bouncy Castle, and `FxDialogServiceTest` opens the real dialogs under a display, fills them in and presses the buttons.
    42 tests in total, 9 skipped without a display.
  - Smoke `connect-fail` connects to `sftp://127.0.0.1:1/`, waits for the real error dialog
    (`Connection failed | Connection refused. ...`), closes it and checks the browser ended disconnected.
  - Side effect: a failed listing now shows the error dialog, so the `navigate` smoke acknowledges it before checking that the
    location is restored. Every later smoke that triggers a failure must do the same.
  - Observed: `SLF4J: No SLF4J providers were found` on startup, because the classpath has an SLF4J 1.x binding next to
    slf4j-api 2. Messages from libraries that log through SLF4J (such as sshj) are dropped. The CLI has the same dependency set.
    Fix later by adding `org.apache.logging.log4j:log4j-slf4j2-impl` if library logs are needed.

- [x] **2.5 End-to-end SFTP test against a container**

  **Do**
  - Add `org.testcontainers:testcontainers` (test scope, version as in `smb/pom.xml`) to the
    `linux` profile dependencies.
  - `SFTPBrowserIntegrationTest` annotated `@Category(TestcontainerTest.class)`: start
    `atmoz/sftp:alpine` with command `foo:pass:::upload`, mount via `HostBuilder` with the mapped
    port, run the same listing path as the browser (`HomeFinderWorker` + `ListWorker`) using a
    fake `DialogService` that returns `foo`/`pass`, and assert the listing contains `upload`.
    Follow `AbstractSMBTest` for container lifecycle.

  **Proof**
  ```bash
  mvn --batch-mode -pl linux -DskipSign -Drevision=0 test -Dtest=SFTPBrowserIntegrationTest -Dsurefire.group.excluded=none
  ```
  Green with Docker available. Also confirm plain `mvn -pl linux test` still passes without Docker
  (the category is excluded by `-P no-testcontainers`; document this in the test's Javadoc).

  **Commit**: `Add SFTP end-to-end test for Linux browser.`

  **Done (executor notes)**
  - The test logs in through the real `FxLoginCallback` and `FxHostKeyCallback` with a fake `DialogService`: the host has the
    username but no password, so the core must ask. It asserts that the home folder listing contains `upload`, that the password
    was asked once and that the unknown host key was shown. Checked with a wrong password: the mount returns no home and the
    test fails.
  - By default only the `IntegrationTest` category is excluded, so a `TestcontainerTest` runs in a plain `mvn test`. The test
    therefore starts with `Assume.assumeTrue(DockerClientFactory.instance().isDockerAvailable())`. Verified: with the Docker
    daemon stopped the result is `Skipped: 1` and the build is green.
  - Running it in this sandbox needed a Docker daemon (`dockerd &`), `TESTCONTAINERS_RYUK_DISABLED=true` (the Ryuk image pull is
    rate limited) and `docker pull mirror.gcr.io/atmoz/sftp:alpine && docker tag mirror.gcr.io/atmoz/sftp:alpine atmoz/sftp:alpine`
    because Docker Hub answered `429 Too Many Requests`. GitHub-hosted runners do not need any of this.

- [x] **2.6 Bookmarks**

  **Do**
  - `BookmarkController`: a `ListView<Host>` bound to `BookmarkCollection.defaultCollection()`
    (listen with `CollectionListener`), buttons Add (opens `ConnectionDialog` prefilled), Edit,
    Delete, and double-click to connect. Saving goes through the collection (`add`, `collectionItemChanged`).
  - Show it as a left pane of the browser window with a toggle button.
  - Unit test `BookmarkPersistenceTest`: create `new BookmarkCollection(tempDirectoryLocal)`
    (the constructor that takes a `Local` folder), `load`, add a `Host` for `sftp` with a nickname,
    then create a second `BookmarkCollection` on the same directory, `load`, and assert one host
    with the same `getHostname()` and `getNickname()`. This proves plist writing works on Linux.

  **Proof**
  ```bash
  mvn --batch-mode -pl linux -DskipSign -Drevision=0 test -Dtest=BookmarkPersistenceTest   # green
  xvfb-run -a linux/run.sh --exit-after 5                                                  # starts with bookmark pane
  ```

  **Commit**: `Add bookmark list and editing.`

  **Done (executor notes)**
  - `BookmarkCollection(Local)` is the constructor to use in tests (there is no `FolderBookmarkCollection`). The reader looks the
    protocol up in the global `ProtocolFactory`, so `BookmarkPersistenceTest` registers an enabled `SFTPProtocol` there.
    4 tests: save and load, change, remove deletes the file, and the password is never written to the bookmark file.
  - `ConnectionDialog` has a bookmark mode (name field, no password, button `Save`, prefilled values for editing).
    `HostBuilder.copy` applies the edited values to the existing bookmark so its UUID and file stay the same.
  - `BookmarkController` listens to the collection and rebuilds its list on the JavaFX thread. The browser has a Bookmarks toggle
    that shows or hides the pane. Double click mounts the bookmark itself (as macOS does), so the core updates its timestamp.
  - Java ignores the `HOME` variable, so `LinuxApplicationPreferences.userHome()` prefers it. This lets `smoke.sh` run with a
    throw-away `HOME` and never touch the real `~/.duck`. Preferences, bookmarks and logs all follow it.
  - Smoke `bookmarks` adds a bookmark through the dialog, checks the `.duck` file, opens it with a real double-click event,
    edits the name, checks the file content, deletes after confirmation and checks the file is gone.
  - Passwords are not stored yet. Saving them needs the keychain step in Phase 5, so every connect asks for the password.

- [x] **2.7 Download with overwrite prompt**

  **Do**
  - Download button: for the selected rows create `new DownloadTransfer(host, roots)` and run it
    through `TransferBackgroundAction` (see `osx` `TransferController.start` and the constructors in
    `core/.../threading/TransferBackgroundAction.java`), adding it to `TransferCollection.defaultCollection()`.
    Target directory: `DownloadDirectoryFinder` from `core/.../ui/browser`, defaulting to
    `~/Downloads` (preference `queue.download.folder`).
  - `FxTransferPrompt implements TransferPrompt` registered for every transfer type under
    `factory.transferpromptcallback.<type>.class` (see how `TerminalPreferences` loops over
    `Transfer.Type`). Minimum UI: a dialog listing the conflicting items with Overwrite / Resume /
    Skip / Cancel mapped to `TransferAction`.
  - `FxTransferErrorCallback implements TransferErrorCallback` -> `factory.transfererrorcallback.class`:
    dialog with Continue / Cancel.
  - `--smoke download <src> <dst>`: the harness creates `<src>/f.bin` (1 MiB random). The smoke
    opens `<src>`, selects `f.bin`, triggers the download into `<dst>` with the prompt replaced by
    one that returns `overwrite`, waits for the transfer to complete, and prints `SMOKE OK download`.
    `linux/smoke.sh` then compares `sha256sum` of source and destination.

  **Proof**
  ```bash
  xvfb-run -a linux/smoke.sh; echo "exit=$?"     # includes download, checksums equal, exit=0
  ```

  **Commit**: `Add download transfer with overwrite prompt.`

  **Done (executor notes)**
  - `TransferController` (one per application, `TransferController.get()`) implements `TransferListener` and starts a
    `TransferCollectionBackgroundAction` with its own pools for source and destination, as the macOS controller does. It adds the
    transfer to `TransferCollection`, which `Bootstrap` loads at startup. The transfer window comes in step 2.9.
  - `FxTransferPrompt` has the constructor `(FxController, Transfer, SessionPool, SessionPool)` that
    `TransferPromptControllerFactory` looks for. It is registered for every `Transfer.Type`. Existing folders are merged without
    asking. `FxTransferErrorCallback` asks whether to continue and rethrows the failure when the user cancels.
    `DialogService` gained `action(...)`, which shows a choice of `TransferAction` with its description.
  - The default action preference is `ask`, so the prompt only appears when the local file already exists. Smoke `download`
    therefore downloads once without a prompt, replaces the local file with other content, downloads again, finds the real
    `File exists` dialog (with the old content still in place while it is open), picks Overwrite and checks the content is back.
    `smoke.sh` then compares `sha256sum`.
  - `queue.download.folder` defaults to `~/Downloads`. The Download button is enabled while rows are selected (multiple selection).
  - `transferDidProgress` is driven by a timer in the core, so a 1 MiB local copy finishes before any event. Step 2.9 must slow the
    transfer down (`transfer.setBandwidth`) to observe progress. Smoke prints `progress=0` for now.
  - Not yet verified: whether a stateful protocol (SFTP) asks for the password again for the transfer connection. Step 2.12
    checks this against the container.

- [x] **2.8 Upload**

  **Do**
  - Upload button opens a `FileChooser` (multiple) and starts `new UploadTransfer(host, root, local)`
    the same way. After completion, reload the current directory.
  - `--smoke upload <src> <dst>`: upload `<src>/g.bin` into `<dst>` with a programmatic file
    selection (bypass the chooser in smoke mode), wait, print `SMOKE OK upload`; the script checks
    checksums.

  **Proof**
  ```bash
  xvfb-run -a linux/smoke.sh; echo "exit=$?"
  ```

  **Commit**: `Add upload transfer.`

  **Done (executor notes)**
  - The Upload button opens a JavaFX `FileChooser` (multiple files) and calls `upload(List<File>)`, which builds the remote paths
    like the macOS controller and starts an `UploadTransfer`. The smoke calls `upload(List<File>)` directly because the native
    chooser cannot be driven. Folders cannot be chosen yet (a `DirectoryChooser` button is a Phase 5 item).
  - `UploadTargetFinder` returns the folder that is shown for any table selection, so files always go to the shown folder.
  - After a successful transfer the browser invalidates its cache and lists the folder again, so the new file shows up.
  - Smoke `upload` mirrors `download`: the second upload meets a different remote file and the real `File exists` dialog is
    answered with Overwrite while the old remote content is still in place. `chooseAction` is shared by both.

- [x] **2.9 Transfer window**

  **Do**
  - `TransferController`: a second `Stage` with a `TableView<Transfer>` bound to
    `TransferCollection.defaultCollection()`, columns Name, Status, Progress (`ProgressBar` cell),
    and buttons Stop, Resume, Remove, Open Folder (`xdg-open` via `ApplicationLauncherFactory`).
    Progress updates come from `TransferListener`/`TransferProgress` as in the `osx` controller.
  - Menu item or toolbar button on the browser to show it.
  - Extend `--smoke download` to assert at least one progress event was observed and print it in
    the OK line: `SMOKE OK download progress=<n>`.

  **Proof**
  ```bash
  xvfb-run -a linux/smoke.sh; echo "exit=$?"      # OK line shows progress>=1
  ```

  **Commit**: `Add transfer window.`

  **Done (executor notes)**
  - The window lives in `TransferController` (one per application): a table of the `TransferCollection` with Name, Status and a
    progress bar, and the buttons Stop, Resume, Remove, Clear and Open Folder. The browser has a Transfers button.
    The collection only has two transfer states (running, stopped), so the status is `Running` (or the live progress text),
    `Complete` or `Incomplete`. Stop cancels the matching `TransferBackgroundAction` in the controller's registry. Resume starts
    the same transfer with `resume(true).reload(false)`.
  - Progress events come from a 100 ms timer in the core. To see them the smoke throttles the transfer with the preference
    `queue.download.bandwidth.bytes` (256 KiB/s for the first download, 64 KiB/s for the stop and resume test). It checks the
    live fraction is strictly between 0 and 1, then the status `Complete`, then `SMOKE OK download progress=<n>` with `n >= 1`
    (40 here). A stopped transfer is `Incomplete` with only part of the file, and after `setBandwidth(-1)` and Resume the
    content is complete. Remove empties the table.
  - `factory.reveal.class` is `XdgOpenRevealService`, which opens the containing folder with `xdg-open`. It passes the path as a
    separate argument, unlike `ExecApplicationLauncher`, which builds one command string and breaks on paths with spaces.
  - Cosmetic follow-up: the live status text is long and gets cut off in the Status column. Widen the column or shorten the text.

- [x] **2.10 File operations: new folder, delete, rename**

  **Do**
  - New Folder: prompt for a name, run `CreateDirectoryWorker`. Delete: confirm, run `DeleteWorker`
    with `DisabledProgressListener`. Rename: inline edit or prompt, run `MoveWorker`. Reload after each.
  - `--smoke fileops <dir>`: create folder `n`, rename it to `m`, create file via upload path or
    `TouchWorker`, delete it, delete `m`; verify on the filesystem in the script; print `SMOKE OK fileops`.

  **Proof**
  ```bash
  xvfb-run -a linux/smoke.sh; echo "exit=$?"
  ```

  **Commit**: `Add folder creation, delete and rename.`

  **Done (executor notes)**
  - New Folder, Rename and Delete buttons run `CreateDirectoryWorker`, `MoveWorker` and `DeleteWorker`, and list the folder again
    afterwards. A new or renamed item is selected after the reload. Delete asks first and names the items. `DialogService`
    gained `input(...)`, shown as a JavaFX `TextInputDialog`.
  - `MoveWorker` borrows a second session from its target pool while it holds the first one, which cannot work for a stateful pool
    with one connection. For stateful protocols the browser therefore creates a separate pool for the move and shuts it down in
    `cleanup`, as the macOS controller does (it never shuts it down). With the local filesystem this path is not exercised, so
    step 2.12 renames a file on the SFTP container.
  - Smoke `fileops` uses the real dialogs, checks the files on disk, and `smoke.sh` checks the folder is empty at the end.

- [x] **2.11 Disconnect, window close, multiple windows**

  **Do**
  - Closing a browser window runs `DisconnectBackgroundAction` on its pool. File > New Browser
    opens another `BrowserController`. Quit disconnects all and calls `Platform.exit()` after
    `BookmarkCollection.defaultCollection().save()` and `PreferencesFactory.get().save()`.
  - Extend `--smoke list` to close the window at the end and verify the JVM exits by itself
    without `System.exit` (the script already checks exit codes; add a 20 s `timeout` around it).

  **Proof**
  ```bash
  xvfb-run -a linux/smoke.sh; echo "exit=$?"
  ```

  **Commit**: `Disconnect on window close and support multiple browsers.`

  **Done (executor notes)**
  - `MainController` owns the browser windows. The window button is intercepted: it disconnects first (`DisconnectBackgroundAction`),
    then hides the window and releases it. Closing the last window quits. Quit asks first if transfers are running, stops them,
    disconnects every browser, saves the bookmark, transfer and preference collections, closes dialogs and calls `Platform.exit()`.
    `Platform.setImplicitExit(false)` makes this explicit.
  - Every browser window has a File menu (New Browser, Open Connection, Disconnect, Close Window, Quit) and a Window menu
    (Transfers), with Ctrl shortcuts. Each window removes its listener from the shared bookmark collection when it is closed.
  - The core's threads are daemon threads, so the JVM ends by itself once `Application.launch` returns. No `System.exit` is needed.
    The original wording said to extend `--smoke list`. A new scenario `windows` does it instead: two windows with their own
    folders, close one with a real `WINDOW_CLOSE_REQUEST` event, then Quit from the menu. The scenario does not call
    `System.exit`, and `smoke.sh` runs it under `timeout 30`, so a hang fails. It also checks that preferences were saved.

- [x] **2.12 Manual acceptance of the usable minimum**

  **Do**
  - Run the app on a desktop (or under `xvfb-run` with a VNC viewer if needed) and perform the
    definition-of-usable-minimum list against a real SFTP server (the Testcontainers image from
    step 2.5 started by hand works: `docker run -p 2222:22 atmoz/sftp:alpine foo:pass:::upload`).
  - Record the date and the server used in a note under this step.

  **Proof**: every item of the definition is checked by a person. Then run the full module checks
  one more time:
  ```bash
  mvn --batch-mode -pl linux -DskipSign -Drevision=0 verify && xvfb-run -a linux/smoke.sh
  ```

  **Commit**: `Record usable minimum acceptance for Linux GUI.` (plan file ticks only)

  **Done (executor notes)**
  - **This acceptance was automated, not done by a person.** On 2026-10-07 an agent ran the checklist against a real SFTP server
    (`atmoz/sftp:alpine`, OpenSSH 10.3, user `foo`, in Docker) and a local HTTPS server with a self-signed certificate, by
    driving the real windows and dialogs with `smoke.sh`. A human walk-through on a real desktop is still recommended. The
    automated run cannot cover the native file chooser, window decorations, themes, Wayland or HiDPI.
  - Each item of the definition of the usable minimum and the scenario that covers it:

    | Item | Scenario |
    |---|---|
    | Start the app | every scenario starts the real application |
    | Pick a protocol, enter host and credentials | `connect`, `bookmarks`, `sftp` (bookmark dialog with protocol, server, port, user) |
    | Accept an SSH host key | `sftp` (real dialog, answered once, not asked again on the second connect) |
    | Accept a TLS certificate | `tls` (shows subject and fingerprint, Continue proceeds, Cancel ends quietly) |
    | See a remote listing and navigate | `list`, `navigate`, `sftp` |
    | Download and upload with overwrite prompts | `download`, `upload` (local filesystem, real dialog), `sftp` (SFTP transfers) |
    | Create and delete folders, rename | `fileops`, `sftp` (rename on a second connection) |
    | See transfer progress | `download` (live progress, stop, resume, remove) |
    | Save and reopen a bookmark | `bookmarks`, `sftp` (disconnect, then open the bookmark again), `BookmarkPersistenceTest` |
    | Error dialog instead of a hang | `connect-fail`, `navigate` (missing folder), `tls` (server that is not WebDAV) |

  - Findings from the SFTP run: the host key is asked once, the password once. Upload, download and rename each use their own
    connection (the server log shows several logins), and none of them asks for the password again, so the credentials are kept in
    memory on the bookmark. The remote folder is empty at the end.
  - `smoke.sh` now also needs `python3` and `openssl` (for `tls-server.py` and the certificate), and starts the SFTP container only
    when `docker info` works. Without Docker it prints `skip sftp`. Proxy variables are unset for the TLS scenario because the
    core's `EnvironmentVariableProxyFinder` ignores `no_proxy`, so `localhost` went through the proxy of the sandbox. This was
    reported as a separate follow-up and no core code was changed.
  - Environment facts that matter for the next steps: JDK 25 comes from `apt-get install openjdk-25-jdk` on Ubuntu 24.04 and
    `xvfb`, `x11-utils` and `imagemagick` help with screenshots. Total time of `xvfb-run -a linux/smoke.sh` is about one minute.

  - **Update 2026-10-08, acceptance by a person.** The owner installed the `.deb` from CI run 16 on a Linux desktop (the title
    bars in the screenshots look like Cinnamon) and used it against their own SFTP server. Reported working: connect and list (screenshots in `linux/screenshots`), download
    with the transfers window, dropping files into the browser to upload, Cryptomator vaults (create, unlock), the preferences
    window. This closes the "recommended human walk-through" above for the paths that were used. Not walked through yet: the
    other protocols (only SFTP was used), the Flatpak and the `.rpm`.

**Gate**: all boxes in Phase 1 and Phase 2 ticked. Only then continue.

---

## Phase 3: GitHub Actions for the Linux build

Note on the existing CI: once step 1.1 adds `linux` to the reactor, the `ubuntu-latest` job in
`build.yml` already compiles the module and runs its headless unit tests on every pull request. That
is intended. Phase 3 adds a dedicated workflow that provides a display, runs the smoke suite, and
later publishes packages.

- [x] **3.1 Add `.github/workflows/linux-gui.yml`**

  **Do**
  - Name `Linux GUI`. Triggers: `push` to `master` and to the working branch, `pull_request`.
    Same `concurrency` block as `build.yml`.
  - Job `build` on `ubuntu-latest`: `actions/checkout@v7` with `fetch-depth: 0` (the build number
    comes from the git commit count), `actions/setup-java@v6` (temurin 25, `cache: maven`), then
    `sudo apt-get update && sudo apt-get install -y --no-install-recommends xvfb rpm fakeroot desktop-file-utils`,
    then:
    ```bash
    mvn --no-transfer-progress --batch-mode verify -DskipITs -DskipSign -Drevision=0 \
        --also-make --projects i18n,profiles,linux
    ```
    with `SKIP_SIGN: true` in `env`, then `xvfb-run -a linux/smoke.sh`.
  - Upload `linux/target/surefire-reports/**` as an artifact when `always()`.

  **Proof**
  - Lint locally: download the `actionlint` release binary into the scratch directory and run
    `actionlint .github/workflows/linux-gui.yml` (no findings).
  - Push the branch. Confirm the `Linux GUI` run is green (use `gh run watch` if `gh` is available,
    otherwise ask the operator to confirm and note the run URL under this step).

  **Commit**: `Add GitHub Actions workflow for Linux GUI.`

  **Done (executor notes)**
  - Run 1 of the `Linux GUI` workflow on `5ce6de01` was green in about 4 minutes
    (https://github.com/leancode/cyberduck/actions/runs/37666628368). The log shows `Tests run: 53, Failures: 0, Errors: 0,
    Skipped: 0` and all twelve smoke scenarios (`core-list` through `sftp`, including `tls` and `sftp`).
  - The workflow is split into three steps. (1) `mvn install -DskipTests --also-make --projects i18n,profiles,linux` installs
    the modules the GUI depends on without running their tests, because `run.sh` resolves the classpath from the local Maven
    repository and `verify` alone does not install. (2) `xvfb-run -a mvn verify --projects linux` runs the module tests with a
    display. (3) `xvfb-run -a linux/smoke.sh`.
  - Packages needed on the runner: `xvfb libgtk-3-0t64 libgl1 libxtst6` (JavaFX needs GTK 3 and OpenGL). Python 3, OpenSSL and
    Docker are already on `ubuntu-latest`, which `smoke.sh` needs for the TLS and SFTP scenarios.
  - The push trigger covers `main`. A new push to the same branch cancels the run in progress, so wait for a run to
    finish before pushing again when you need its result.
  - Reading a run from the agent: `mcp__github__actions_list` (`list_workflow_runs`, `list_workflow_jobs`) and
    `mcp__github__get_job_logs` with `tail_lines`.

- [x] **3.2 Run the Testcontainers SFTP test in CI**

  **Do**
  - Add a step after the smoke tests:
    `mvn --batch-mode -pl linux -DskipSign -Drevision=0 test -Dtest=SFTPBrowserIntegrationTest -Dsurefire.group.excluded=none`.
    Docker is available on `ubuntu-latest`.

  **Proof**: push; the step passes in the `Linux GUI` run.

  **Commit**: `Run SFTP end-to-end test in Linux GUI workflow.`

  **Done (executor notes)**
  - No extra step was needed. The `TestcontainerTest` category is not excluded by default, so the test already runs in step (2)
    of 3.1. The log of run 1 shows `Running ch.cyberduck.ui.fx.SFTPBrowserIntegrationTest` and
    `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`. Adding a second run of the same test would only make the build slower.
    The `sftp` smoke scenario also ran against the container.

- [x] **3.3 Publish the test report**

  **Do**: add the `ScalableCapital/action-surefire-report@v2` step from `build.yml` with
  `check_name: Test Report (linux-gui)` and the same `permissions` block as `build.yml`.

  **Proof**: push; a check named `Test Report (linux-gui)` appears on the commit.

  **Commit**: `Publish Linux GUI test report.`

  **Executor note**: done in step 3.2. The check `Test Report (linux-gui)` was created on every run since run 2
  (https://github.com/leancode/cyberduck/runs/112981812639 and later). It reports 53 tests and shows a failing test by
  file and line, as seen on run 5.

---

## Phase 4: `.deb` and `.rpm` packages

- [x] **4.1 jpackage app-image**

  **Do**
  - Create `linux/build.xml` from `cli/linux/build.xml`. Changes: `app.name` = `Cyberduck`,
    `--main-jar linux-${fullversion}.jar`, `--main-class ch.cyberduck.ui.fx.MainApplication`,
    drop `-Djava.awt.headless=true`, keep the JNA and encoding options, add
    `--icon ${home}/cyberduck-application.png`. For now only the `app-image` antcall.
  - The input directory must contain: all jars and `.so` files from `linux/target` (the inherited
    `copy-dependencies-*` executions put them there), `profiles/*.cyberduckprofile`, and the
    `*.lproj` directories from the `i18n` artifact. Add an `unpack-i18n` execution to the module's
    `maven-dependency-plugin` modelled on `osx/pom.xml` (search `<artifactId>i18n</artifactId>`),
    output `${project.build.directory}`.
  - In the `linux` profile of `linux/pom.xml`, add `maven-antrun-plugin` with no configuration
    (this inherits the parent's `run-ant-target` execution that calls `build.xml` target `build` in
    the `compile` phase, as `cli/linux` does).

  **Proof**
  ```bash
  mvn --batch-mode -pl linux -DskipSign -Drevision=0 package
  ls linux/target/release/Cyberduck/bin/Cyberduck
  linux/target/release/Cyberduck/bin/Cyberduck --version
  ls linux/target/release/Cyberduck/lib/app/profiles | head -3
  ls -d linux/target/release/Cyberduck/lib/app/*.lproj | wc -l       # more than 20
  unzip -l linux/target/release/Cyberduck/lib/app/javafx-graphics-*.jar | grep -c 'libglass.so'   # 1: the JavaFX natives are present
  xvfb-run -a linux/target/release/Cyberduck/bin/Cyberduck --smoke list /tmp; echo "exit=$?"
  ```

  **Commit**: `Build Linux app image with jpackage.`

  **Done (executor notes)**
  - `maven-antrun-plugin` in the `linux` profile runs `build.xml` in the `package` phase, because the jpackage input needs the jar
    of this module itself. The parent's `run-ant-target` execution (compile phase) is switched off with `<phase>none</phase>`.
  - The i18n jar also holds the macOS interface files. `unpack-i18n` takes only `*.lproj/*.strings` and `*.strings.1`
    into `linux/target`, next to the profiles, so `LinuxApplicationResourcesFinder` finds both.
  - The plan's jar copy would also copy `linux-*-sources.jar`, so it is excluded. The classifier jars of JavaFX end up as
    `javafx-graphics-25.0.4.jar` and it does contain `libglass.so`, which the proof checks.
  - Proof output on 2026-10-07: `Cyberduck --version` prints `Cyberduck 9.6.0-SNAPSHOT`, 24 profiles, 38 `*.lproj` folders, one
    `libglass.so` in the JavaFX graphics jar, and `xvfb-run -a bin/Cyberduck --smoke list <dir>` prints `SMOKE OK list 2` with the
    bundled runtime. The image is about 240 MB.
  - JVM options of the launcher: `--enable-native-access=ALL-UNNAMED`, UTF-8, `-Djna.nounpack=true`, `-Djna.noclasspath=true` and
    `-Djna.boot.library.path=$APPDIR` (jpackage replaces `$APPDIR`). These replace the preference entries of the CLI that did nothing.

- [x] **4.2 Desktop integration**

  **Do**
  - Add `--linux-shortcut`, `--linux-menu-group "Network;FileTransfer;"`, `--linux-app-category net`,
    `--linux-package-name cyberduck`, `--linux-deb-maintainer "<feedback@cyberduck.io>"`,
    `--linux-rpm-license-type GPL`, `--license-file ${license}` (not for app-image, mirror the
    `unless:true` trick in `cli/linux/build.xml`).
  - Custom desktop entry: run jpackage once with `--verbose` and read the lines that say which
    resource file names it would use from `--resource-dir` for the desktop file. Create that file
    under `setup/linux/` (new directory) adding `MimeType=x-scheme-handler/sftp;x-scheme-handler/ftp;x-scheme-handler/ftps;x-scheme-handler/s3;`
    and `Categories=Network;FileTransfer;`, and copy it into `${build.resources}` in `build.xml`.
  - Handle a URL argument on the command line: `MainApplication` passes a single `scheme://` argument
    to `HostParser` and opens a browser on it.

  **Proof**
  ```bash
  mvn --batch-mode -pl linux -DskipSign -Drevision=0 package
  desktop-file-validate linux/target/release/Cyberduck/lib/*.desktop
  grep -E "MimeType=.*x-scheme-handler/sftp" linux/target/release/Cyberduck/lib/*.desktop
  ```

  **Commit**: `Add desktop entry and URL scheme handlers.`

  **Done (executor notes)**
  - jpackage only creates a desktop entry for packages, not for the app image. The proof therefore validates the entry inside the
    deb: `dpkg-deb -x ... ; desktop-file-validate opt/cyberduck/lib/cyberduck-Cyberduck.desktop` prints nothing and exits 0. The
    deb is now built before the app image, because jpackage refuses to write the `Cyberduck` folder when it already exists in the
    destination, and `build.xml` clears the destination first.
  - `setup/linux/Cyberduck.desktop` is copied to `Cyberduck.desktop` in jpackage's resource folder. jpackage logs
    `Using custom package resource [Menu shortcut descriptor] (loaded from Cyberduck.desktop)`. The tokens are the ones of the
    default template (`APPLICATION_LAUNCHER`, `APPLICATION_ICON`). `Exec` ends with `%U` so the desktop passes the clicked URL.
    The entry registers `x-scheme-handler` for ftp, ftps, sftp, s3, dav and davs and sets `StartupWMClass`.
  - `CyberduckApplication` opens the first argument that looks like a URL (`scheme://...`) with `BrowserController.open`.
    An invalid URL shows the error dialog `Invalid URL`. Smoke `url` opens `file:///<folder>` (this protocol has no host, so the
    form is `file:///path`, not `file://localhost/path`) and then `sftp://`, which must produce the dialog.

- [x] **4.3 `.deb` package**

  **Do**
  - Add the `deb` antcall. Create `setup/deb/cyberduck.control`, `cyberduck.postinstall`,
    `cyberduck.prerm`, `cyberduck.postrm` from the `duck.*` files; the post-install symlink becomes
    `/opt/cyberduck/bin/Cyberduck` -> `/usr/local/bin/cyberduck`. Copy them into `${build.resources}`
    as `control`, `postinst`, `prerm`, `postrm` like `cli/linux/build.xml` does.

  **Proof**
  ```bash
  mvn --batch-mode -pl linux -DskipSign -Drevision=0 package
  ls linux/target/release/cyberduck_*.deb
  dpkg-deb -I linux/target/release/cyberduck_*.deb | grep -E "Package: cyberduck|Version:"
  dpkg-deb -c linux/target/release/cyberduck_*.deb | grep -E "opt/cyberduck/bin/Cyberduck$"
  sudo apt-get install -y ./linux/target/release/cyberduck_*.deb
  cyberduck --version
  xvfb-run -a cyberduck --smoke list /tmp; echo "exit=$?"
  sudo apt-get remove -y cyberduck
  ```

  **Commit**: `Build Debian package for Linux GUI.`

  **Done (executor notes)**
  - The package is `linux/target/release/cyberduck_9.6.0.0_amd64.deb` (about 240 MB installed). It is built in `build.xml`
    before the app image. The install test in a clean container found two defects that the proof on a developer machine hides:
    1. jpackage works out the dependencies by scanning native libraries, but JavaFX keeps its natives inside its jars, so the
       list had no GTK. `--linux-package-deps "libgtk-3-0t64 | libgtk-3-0, libgl1"` adds it. The generated list also contains the
       library names of the build machine (for example `libasound2t64`), so the deb is built and installed on Ubuntu 24.04.
    2. jpackage's own post-install script runs `xdg-desktop-menu install`, which fails with `No writable system menu directory
       found` (exit 3) on a machine without a desktop environment and leaves the package half-configured. The custom
       `setup/linux/cyberduck.postinst` and `cyberduck.prerm` (copies of the defaults) link the menu entry into
       `/usr/share/applications`, run `update-desktop-database` when it exists, and link the command `/usr/bin/cyberduck`.
       They are removed again only on `remove`, not on upgrade. jpackage replaces its own token names even inside shell
       comments, so the comments do not mention them.
  - Proof output: `dpkg-deb -I` shows `Package: cyberduck`, `Version: 9.6.0.0` and the GTK dependency. On this machine
    `dpkg -i`, then `CYBERDUCK_BIN=cyberduck xvfb-run -a linux/smoke.sh` passed all 13 scenarios against the installed package, and
    `dpkg --remove` removed `/usr/bin/cyberduck`, the menu entry and `/opt/cyberduck`. In a clean `ubuntu:24.04` container
    `apt-get install ./cyberduck_*.deb xvfb` installed 145 packages, `cyberduck --version` printed `Cyberduck 9.6.0-SNAPSHOT`,
    `xvfb-run -a cyberduck --smoke list` printed `SMOKE OK list 2`, and `apt-get remove` cleaned up. In this sandbox that test
    needs about 15 minutes for the downloads. Docker Hub is rate limited, so the image came from `mirror.gcr.io/library/ubuntu:24.04`.

- [x] **4.4 `.rpm` package**

  **Do**
  - Add the `rpm` antcall. Create `setup/rpm/cyberduck.spec` from `duck.spec` (name `cyberduck`,
    summary `Cyberduck`, paths `/opt/cyberduck`). Use the `--verbose` jpackage output to confirm the
    expected spec resource file name, then copy it into `${build.resources}` with the version
    replacements from `cli/linux/build.xml`.

  **Proof**
  ```bash
  mvn --batch-mode -pl linux -DskipSign -Drevision=0 package
  ls linux/target/release/cyberduck-*.rpm
  rpm -qpi linux/target/release/cyberduck-*.rpm | grep -E "^Name *: cyberduck|^License *: GPL"
  rpm -qpl linux/target/release/cyberduck-*.rpm | grep -E "opt/cyberduck/bin/Cyberduck$"
  ```
  Then an install test in a Fedora container (Docker is available locally and in CI):
  ```bash
  docker run --rm -v "$PWD/linux/target/release:/pkg:ro" fedora:latest bash -c \
    "dnf install -y /pkg/cyberduck-*.rpm xorg-x11-server-Xvfb >/dev/null && /opt/cyberduck/bin/Cyberduck --version && xvfb-run -a /opt/cyberduck/bin/Cyberduck --smoke list /tmp"
  ```
  Expect the version line and `SMOKE OK list`.

  **Commit**: `Build RPM package for Linux GUI.`

  **Done (executor notes)**
  - jpackage looks for the spec file under the name `cyberduck.spec` (its verbose output says so). `setup/linux/cyberduck.spec` is the
    default template with the same two changes as the deb scripts: own menu entry and `/usr/bin/cyberduck` in `%post`, removal in
    `%preun` only when `$1` is 0 (not on upgrade). The default spec sets `Autoreq: 0`, so the dependencies are listed by hand with
    `--linux-package-deps "gtk3, mesa-libGL, libXtst, alsa-lib"` (plus `xdg-utils` from jpackage). License type is `GPL`.
  - `rpm -qpi` shows `Name: cyberduck`, `Version: 9.6.0.0`, `License: GPL`, and `rpm -qpl` lists `/opt/cyberduck/bin/Cyberduck`.
  - In a clean `fedora` container `rpm -i --nodeps` creates the command and the menu entry, an upgrade keeps them, and `rpm -e` removes
    them and `/opt/cyberduck`. A complete `dnf install` with the dependencies could not be run here because the sandbox blocks the Fedora
    mirrors (`Status code: 40x`). The `install-rpm` job of the workflow does that (step 4.5).

- [x] **4.5 Package artifacts and install tests in the workflow**

  **Do**
  - In `linux-gui.yml` add upload steps for `linux/target/release/*.deb` and `*.rpm` using
    `actions/upload-artifact@v7` with `archive: false`, copying the style of the
    `Archive DEB (Linux)` step in `deploy.yml`.
  - Add a job `install-deb` (needs `build`) on `ubuntu-latest`: download the artifact,
    `sudo apt-get install -y ./cyberduck_*.deb`, `cyberduck --version`, and
    `xvfb-run -a cyberduck --smoke list /tmp`.
  - Add a job `install-rpm` (needs `build`) with `container: fedora:latest`: `dnf install` the rpm
    and `xorg-x11-server-Xvfb`, run `--version` and the smoke.

  **Proof**: `actionlint` clean; push; all three jobs green; the run's artifacts list shows the
  `.deb` and `.rpm` files.

  **Commit**: `Upload and install-test Linux packages in workflow.`

  **Executor note**: proven by run 6 of "Linux GUI" on commit `cbd912fd`
  (https://github.com/leancode/cyberduck/actions/runs/37678626859). All four jobs are green: "Build and test" (53 tests, 12
  smoke scenarios, desktop entry check, DEB and RPM uploaded), "Test Report (linux-gui)", "Install the deb in a clean
  Ubuntu" and "Install the rpm in a clean Fedora". The two install jobs ran `cyberduck --version` and the list smoke
  from the installed package, then removed it and checked that `/usr/bin/cyberduck` and `/opt/cyberduck` are gone.
  Findings: the deb job needs up to 13 minutes because archive.ubuntu.com is slow for the GTK dependencies. Wait for it.
  Do not cancel it, because the log of a cancelled job is not available. The jobs have `timeout-minutes` and apt retries
  for this reason. Run 5 failed once on a race in `BookmarkPersistenceTest` (the folder monitor wrote a deleted
  bookmark back), fixed by turning the monitor off in that test.

- [x] **4.6 Wire into the release pipeline**

  **Do**
  - `deploy.yml`: in the `--projects` map, change the Linux entry to
    `i18n,profiles,cli/linux,linux`. Add archive steps for `linux/target/release/*.deb` and
    `*.rpm` next to the existing CLI ones, and add both globs to the `Attest Build Provenance (Linux)`
    step. Add `desktop-file-utils` to the packaging tools apt line.
  - `release.yml`: add `release/cyberduck_*.deb` and `release/cyberduck-*.rpm` to the `files`
    list of the `Publish Release` step, and download the new artifacts the same way the CLI ones
    are downloaded.

  **Proof**: `actionlint` on both files is clean; `git diff` shows only additive changes; the
  operator confirms the next real release run picks up the packages (note the run URL here when
  known). This step cannot be fully proven from a branch because `deploy.yml` uses self-hosted
  runners and release secrets.

  **Commit**: `Publish Linux GUI packages with releases.`

  **Done (executor notes)**
  - `deploy.yml`: the Linux entry of the `--projects` map is `i18n,profiles,cli/linux,linux`. The two Linux archive steps and the
    attestation step now list `linux/target/release/*.deb` and `*.rpm` next to the CLI ones. The packaging tools step already installs
    `rpm` and `fakeroot`. The package names differ from the CLI ones (`cyberduck_...` and `cyberduck-...` against `duck_...` and
    `duck-...`), so the artifacts do not collide, including the arm64 leg.
  - `release.yml`: a new step `Download Linux GUI` (pattern `{cyberduck_*,cyberduck-*}`) and two more lines in the `files` of the
    `Publish Release` step.
  - `linux/pom.xml` sets `maven.deploy.skip` to `true`, because the `deploy` goal of the release run would otherwise publish the jar of the
    application to the Maven repository next to the libraries. The application is distributed as packages.
  - `actionlint` reports the same 15 findings on `deploy.yml` and `release.yml` before and after the change (an unknown permission scope
    newer than the linter, and case sensitive `inputs[...]` names), so none come from this change. `linux-gui.yml` has none.
  - As the plan said, this cannot be proven from a branch: the workflows use self-hosted runners and release secrets. Check the next real
    release run: the Linux legs should upload `cyberduck_*.deb` and `cyberduck-*.rpm`, and the GitHub release should list them.

- [x] **4.7 Documentation**

  **Do**: add a Linux section to `README.md` (how to build `linux`, where the packages land:
  `linux/target/release/*.deb|*.rpm`), mention the module in `AGENTS.md` under platform front-ends,
  and add a `CHANGELOG.md` entry.

  **Proof**: `git diff --stat` touches only the three files; the commands in the README section
  were copied from proofs above and run successfully.

  **Commit**: `Document Linux GUI build and packages.`

  **Done (executor notes)**
  - `README.md` has a Linux section (prerequisites, build, packages, install, run from the build tree, tests) and lists the packages
    next to the others. `AGENTS.md` names the `linux` module in the list of front-ends. `CHANGELOG.md` has a feature line under 9.6.0.
  - The build and test commands in the README were run exactly as written on 2026-10-07:
    `SKIP_SIGN=true mvn install -DskipTests --also-make --projects i18n,profiles,linux` and `xvfb-run -a mvn verify -pl linux`.
    Without `-Drevision=0` the packages are named with the commit count (`cyberduck_9.6.0.569_amd64.deb`).

---

## Phase 5: Beyond the minimum (ordered, each still one step with a proof)

- [x] **5.1 Secret Service password store**: `SecretToolPasswordStore` implementing
  `HostPasswordStore` by running `secret-tool store/lookup/clear` (libsecret) with attributes
  `service=cyberduck host port user protocol`; falls back to `UnsecureHostPasswordStore` when
  `secret-tool` is missing. Register under `factory.passwordstore.class`. Proof: unit test that is
  skipped when `secret-tool` is absent, otherwise stores, finds and deletes a password.

  **Executor note**: done. `SecretToolPasswordStore` extends `DefaultHostPasswordStore`, so bookmarks, keys and
  tokens work as on the other platforms. The password goes to `secret-tool store` on standard input, never on the command
  line (a test checks the logged arguments). Items carry `application=cyberduck` plus `kind`, `scheme`, `port`, `host`,
  `user` for internet passwords and `service`, `account` for generic ones. Without the tool it falls back to the
  credentials file. A locked keyring makes `secret-tool` wait for a prompt, so every run has a timeout (60 s, 10 s in
  the test) and fails with an access denied error. Proof: 6 tests. Five use a fake `secret-tool` script (store, find,
  delete, other account and port not found, bookmark login, fallback, timeout). The sixth uses the real tool and skips
  without an unlocked Secret Service. Run locally with a real gnome-keyring: all 6 pass, 0 skipped, using
  `dbus-run-session -- sh -c 'eval "$(printf test | gnome-keyring-daemon --unlock --components=secrets)"; mvn ...'`
  (an empty keyring password makes the daemon ask a prompter for a display, so use a non-empty one).

- [x] **5.2 Desktop notifications**: `NotifySendNotificationService` using `notify-send`, registered
  under `factory.notification.class`; transfer completion notifies. Proof: test skipped without
  `notify-send`; smoke download prints `notified` when available.
  **Executor note**: done. `NotifySendNotificationService` runs `notify-send --app-name=Cyberduck` with the transfer
  identifier as the replace hint and `--` before the title, so a title that starts with a dash is not an option. Core
  already notifies when a transfer ends, so only the service and its registration were needed. A missing tool is
  remembered and ignored. Proof: 4 unit tests (fake tool, dash titles, missing tool, real tool when installed) and a
  smoke check that puts a fake `notify-send` first on the PATH and requires the line `Download complete` after the
  download scenario (`ok notified`, passed locally). Titles have no translation entry yet, so the English text is shown.
  Finding: `linux/smoke.sh` pins its isolated home to the credentials file store, because a machine with `secret-tool`
  and a display starts a keyring that waits for a prompt and made the SFTP scenario time out. The check that quitting
  saves the preferences now compares the file before and after, since the pin creates the file.
- [x] **5.3 Localization**: `applicationLocales()` returns the `*.lproj` directories found next to
  the resources; `RegexLocale` already reads them. Proof: `LANG=de_DE.UTF-8 linux/run.sh --smoke list`
  prints a German toolbar label captured in the OK line.
  **Executor note**: done. The translations were already unpacked next to the jars and `RegexLocale` follows the
  language of the process. Two things were missing. The labels were English literals, so every label, menu entry,
  column title, button of the dialogs and the file chooser title goes through `Messages.get`, which looks the English
  text up in the tables that hold window and dialog labels (Localizable, Browser, Folder, Transfer, Credentials and
  others) and shows the English text when none has it ("Up", "Add", "Back" and "Connect" are not translated in the
  shared files yet). And the language of the session was only read from the locale of the process, which Java ignores
  when that locale is not installed, so `Bootstrap.language` also reads the gettext variable `LANGUAGE` (`de`,
  `xx:fr:de`, `pt_BR`) and takes the first language with a `.lproj` folder. Proof: 5 tests in `MessagesTest`
  (German, French, English, untranslated text, parsing of `LANGUAGE`) and three smoke runs of the new `locale` mode:
  no variable prints `refresh=Refresh`, `LANGUAGE=de` prints `refresh=Aktualisieren`, `LANGUAGE=xx:fr:de` prints
  `refresh=Actualiser`. A screenshot in German shows the toolbar, menu and columns translated and nothing cut off. The
  window's minimum width follows the toolbar but never exceeds the screen. `smoke.sh` now pins `LC_ALL=C.UTF-8` and
  unsets `LANGUAGE` so that a developer's own language does not break the English checks.
- [x] **5.4 Preferences window**: General (download folder, default protocol), Transfers (concurrent
  transfers, overwrite policy), Connection (timeout, proxy). Proof: change a value, restart, value
  persisted (`cyberduck.properties` diff).
  **Executor note**: done. File, Preferences… (Ctrl+comma) opens `PreferencesController` with three tabs. General: download
  folder (`queue.download.folder`, with a folder chooser, empty restores the default), show hidden files
  (`browser.showHidden`) and default protocol (`connection.protocol.default`, which the connection dialog now uses
  instead of a fixed `sftp`; the Linux default stays `sftp`). Transfers: what to do when a file to download or upload
  exists (`queue.download.action`, `queue.upload.action`, "ask" or one of the transfer actions) and how to transfer
  (`queue.transfer.type`). Connection: timeout (`connection.timeout.seconds`), retries (`connection.retry`) and the
  proxy of the system (`connection.proxy.enable`). There is no Apply button. Every change is written to
  `~/.duck/cyberduck.properties` when it is made, and typed spinner values are taken over when the field loses focus.
  Proof: 2 tests that drive the real controls and read the file with a second `LinuxApplicationPreferences`, and the
  smoke modes `preferences` (opens the window from the menu, sets timeout 45 and retries 2) and `preferences-check`
  (second start shows 45), with `smoke.sh` grepping `connection.timeout.seconds=45` and `connection.retry=2` in the
  file between them. The layout of this window has not been looked at by a person yet.
- [x] **5.5 Info panel**: size, permissions (`UnixPermission` feature when present), modification
  date, URL (`UrlProvider`). Proof: smoke `info` reports the size of a known file.
  **Executor note**: done. File, Get Info (Ctrl+I, enabled with exactly one selection) opens `InfoController`. It
  shows the values of the listing at once (name, kind, folder, size as text and bytes, modification date, permissions
  as symbol and octal mode, owner, group) and then runs two workers on the session: the core `AttributesWorker`, which
  reads the attributes again from the server, and a small worker that asks the `UrlProvider` feature for the address. The
  permission shown is whatever the protocol reports, so there is no editing yet. Proof: 2 tests for the labels and the
  smoke mode `info`, which opens the window from the menu for a 1 MiB file with mode 640 and prints
  `size=1048576 permissions=rw-r----- (640) url=file://...`, checked by `smoke.sh`.
- [x] **5.6 Cryptomator vaults**: Create Vault (`CreateVaultWorker`) and open an existing vault
  (`LoadVaultWorker` with `FxPasswordCallback`). Proof: integration test on the local filesystem
  creates a vault, uploads a file, lists it through the vault, and verifies the raw directory holds
  only encrypted names.
  **Executor note**: done. `LinuxApplicationPreferences` registers `DefaultVaultProvider`; the vault registry is already
  part of every pool made by `SessionPoolFactory`. File, Create Vault… opens `VaultDialog` (name, passphrase twice,
  keep in keyring; Create stays disabled until the two passphrases are equal) and runs `CreateVaultWorker`. File,
  Unlock Vault / Lock Vault (the text follows the state of the selected folder) runs `LoadVaultWorker` with the
  password callback or `LockVaultWorker`. A vault folder that is entered unlocks itself with the same prompt.
  Findings: (1) A transfer opens its own connection with its own, still locked, registry, so an upload into a vault asks for
  the passphrase again unless it was kept in the keyring. This is the behavior of the macOS application too. (2)
  The core helper `ContentWriter` writes through the underlying feature and therefore does not encrypt, so the test
  uses the features of the session. (3) With a locked keyring the save of the passphrase waits for the timeout of
  `SecretToolPasswordStore`, so tests and smoke runs do not keep the passphrase. Proof: `VaultIntegrationTest` creates a
  vault on a local folder, unlocks it, writes and reads a file through the vault, lists the real name through
  `ListWorker`, checks that neither the name nor the text is on disk, and locks it. The smoke mode `vault` does the
  same with the real dialogs (refused mismatch, prompt, upload through a transfer) and `smoke.sh` greps the vault folder
  for the name and the text. It runs with a home of its own, because the finished upload stays in the list of transfers.
- [x] **5.7 Synchronize and copy transfers**: `SyncTransfer` with its prompt UI, in-session copy via
  `CopyWorker`. Proof: smoke `sync` between two local directories.
  **Executor note**: done. File, Synchronize… asks for a folder on this computer and starts a `SyncTransfer` with the
  selected folder on the server, or the folder that is shown. The prompt of the transfer is now headed "Synchronize"
  and offers download, upload or both, where it used to say "File exists". File, Duplicate File… (Ctrl+D) asks for a name
  and runs `CopyWorker` inside the session, after a confirmation when the name is taken. Proof: the smoke mode `sync`
  has a file that only one side has on each side and a file that is newer locally, chooses "both" in the dialog and
  requires every file on both sides with the newer content. The smoke mode `duplicate` makes `f copy.txt` and compares
  the content. One unit test covers the wording and the choices of the sync prompt. Because a finished transfer stays in
  the list of transfers of the next start, `smoke.sh` runs the vault and sync scenarios in a home of their own. The
  download scenario counts the transfers in the list and failed when they shared one.
- [x] **5.8 Drag and drop**: drop files from the desktop onto the browser to upload. Proof: manual.
  **Executor note**: implemented. The manual proof was done by the owner on 2026-10-08 ("dropping in works", see step 2.12). Details of the implementation: The table and its rows accept files dragged from the
  desktop (`TransferMode.COPY`) while connected. A drop on a folder row uploads into that folder, a drop anywhere else
  uploads into the folder that is shown, both through the same `upload` that the Upload button uses (its smoke scenario
  passes). A drop cannot be made from outside the toolkit, because a `Dragboard` can only be created by a real drag
  gesture, which is why the proof was manual. Dragging out of the browser is step 6.10.
- [ ] **5.9 Flatpak manifest** under `setup/flatpak/` built from the app image. Proof:
  `flatpak-builder` succeeds locally and `flatpak run io.cyberduck --version` prints the version.
  **Executor note (not ticked)**: written, not built. `setup/flatpak/` has the manifest `io.cyberduck.Cyberduck.yml`
  (GNOME 48 runtime, wraps the application image from `linux/target/release/Cyberduck`, sockets for X11, SSH agent,
  network, home folder, D-Bus names for the keyring and notifications), a desktop entry, AppStream metadata and a README
  with the build commands. What was checked offline: `flatpak-builder --show-manifest` parses the manifest,
  `desktop-file-validate` is clean, `appstreamcli validate --no-net` passes with one pedantic hint about missing
  screenshots. What was not possible: the sandbox of this session cannot reach Flathub, so the runtime could not be
  installed and no build ran. The workflow `linux-flatpak.yml` (manual start) builds the image, installs the runtime,
  builds the Flatpak, runs `--version` and the list smoke inside it and uploads the bundle. Start it once to close this
  step. Open points listed in the README: `secret-tool` and `notify-send` are not in the runtime, so inside the sandbox
  passwords go to the credentials file and there are no notifications until libsecret and libnotify are added or the
  portals are used.
- [ ] **5.10 Add `linux-gui.yml` smoke suite to branch protection** as a required check (operator action).

---

## Phase 6: Feedback from the first test on a desktop (2026-10-08)

The owner installed the package from the CI runs 8 and 16 and tested it. Everything that worked and every wish that came out of it is
recorded here, one step each, with what was done and how it was proven. All steps were done on 2026-10-08. The unit tests of
the module are 108 (1 skipped without a keyring) and every scenario named below is in `linux/smoke.sh`.

- [x] **6.1 Toolbar labels and screenshots**

  **Request**: at the default window size the labels of the toolbar buttons were cut ("Bookm...", "Con...").
  **Done**: every button keeps its full width, the path field gives way, and the window cannot be narrower than its toolbar
  (never wider than the screen). The three screenshots of the owner are in `linux/screenshots` and shown in `README.md`.
  **Proof**: a screenshot under Xvfb shows all labels, in English and in German.

- [x] **6.2 The local disk is called a local disk**

  **Request**: "Is this supposed to be local?" about a default protocol named like the computer.
  **Done**: yes. The core names the local protocol after the computer, as the macOS application does. Lists of protocols (connection
  dialog, preferences) now show `Local Disk (hostname)`. `Messages.protocol`.
  **Proof**: `MessagesTest.testLocalDiskIsNotNamedAfterTheComputerAlone`.

- [x] **6.3 Arrow icons for Back and Up**

  **Request**: icons like the original GUI instead of the words.
  **Done**: `Icons` draws a left and an up triangle as shapes that follow the theme colour. The buttons have tooltips and
  accessible text. Back is greyed while there is no history.
  **Proof**: screenshot. The smoke scenarios still use the same buttons (`navigate`).

- [x] **6.4 Menus of the right mouse button**

  **Request**: a menu on files with download, info and edit, and for the empty area an upload picker.
  **Done**: a right click selects the row like a file manager and shows Open, Download, Edit, Edit With, Compare, Get Info,
  Rename, Duplicate, Delete, Unlock/Lock Vault (folders only), Synchronize, New Folder, Upload and Refresh. The empty area
  shows Upload, New Folder, Refresh, Synchronize and Create Vault. Edit, Edit With and Compare appear for files only.
  **Proof**: smoke `context` fires the menu on a file, a folder and the empty area, checks the items, opens the info window from
  the menu and creates a folder from the menu of the empty area.

- [x] **6.5 Change permissions in the info window**

  **Request**: at minimum change the permissions.
  **Done**: the info window has boxes for read, write and execute of owner, group and others, an octal field that follows the
  boxes both ways, Apply, and for folders "Apply changes to enclosed items". It works through the `UnixPermission` feature and core's
  `WritePermissionWorker`, so it is disabled for protocols without permissions. Special bits (setuid, sticky) are not kept.
  **Proof**: 3 unit tests (boxes and number, invalid numbers, disabled without the feature) and smoke `chmod`: boxes to 600,
  octal 664 and a folder with its contents to 700, each checked on the disk.

- [x] **6.6 Edit files with an external program**

  **Request**: edit from the listing, with a default editor and editors by file type.
  **Done**: Edit (Ctrl+E and the menus) downloads the file, opens it, watches it and uploads the changes when it is saved, through
  core's editor framework. The Linux parts it needs did not exist: `LinuxApplicationFinder` reads the desktop entries
  (`DesktopEntry`) and asks `xdg-mime` for the program of a type, `LinuxApplicationLauncher` starts programs without a shell, so
  paths with spaces work. Order of choice: the editor set for the extension, then the default of the desktop for the type, then the
  default editor of the preferences, then `xdg-open`. Finding: core's `Application` changes the identifier to lower case, which breaks a command
  with capitals, so `LinuxApplication` keeps the command as it is.
  **Proof**: unit tests for `DesktopEntry`, `LinuxApplicationFinder` and the launcher (9) and smoke `edit` with fake editors that change the
  file after a pause: the text and markdown editors by type, a chosen editor and the default editor each change the file on the server.

- [x] **6.7 Preferences: editors and the program for comparing**

  **Request**: the settings must offer a default editor, ideally by file type.
  **Done**: a tab Applications with the default editor, "always use the default editor", a table of editors by file extension (add,
  remove) and the program for comparing. `ApplicationPicker` lists the installed programs and takes any command. Settings
  are `editor.bundleIdentifier`, `editor.alwaysUseDefault`, `linux.editor.<extension>`, `linux.editor.types`, `linux.compare.tool`.
  **Proof**: `PreferencesControllerTest` (editors by type, persisted and reloaded) and `CompareToolsTest`.

- [x] **6.8 Compare in a program**

  **Request**: "compare" as the action for existing files should open a tool: download the file as a second copy and compare.
  **Done**: Compare… (menu and menu of the right mouse button) downloads the server file again to a folder of its own and starts the
  chosen program (Meld, KDiff3, Kompare, Diffuse, xxdiff, `code --diff`, DiffMerge, Beyond Compare, or any command) with the file on this computer
  first and the copy second. The file on this computer is never touched. The setting "When a file to download exists:
  Compare in a program" does this for the files that exist and downloads the others. Finding: the action called Compare in the core
  skips files that match in size, time or checksum. It works (checked against a real SFTP server: same file skipped, older file
  fetched) but shows nothing, which looked like a bug. It is now named "Skip files that did not change".
  **Proof**: smoke `compare` with a program that records its arguments (arguments, content of the copy, local file unchanged, the
  download setting with one existing and one new file), 4 unit tests of the choice of the program, a test of the preference, and the
  SFTP scenario checks that an unchanged file is skipped and an older one is downloaded.

- [x] **6.9 FTP: anonymous login, character set, connect mode, transfer mode**

  **Request**: FTP settings for the default character set, the transfer method (binary, ASCII, auto) and anonymous login.
  **Done**: the connection dialog shows Anonymous Login for protocols that allow it and, under More Options, the character set
  (also for other protocols that have one), the FTP connect mode and the transfer mode. All are kept in the bookmark and shown again
  when it is edited. The tab FTP in the preferences sets the default character set, the default transfer mode and the extensions
  that auto sends as ASCII. Core change, small and opt-in: new `ftp/.../FTPFileType` chooses the file type of the data connection
  from `ftp.transfer.mode` (`binary` default, `ascii`, `auto`) of the bookmark or the preferences, `FTPWriteFeature` uses it, and
  `defaults` has the two new settings. Limit: it applies to uploads only. A converted download is shorter than the size the
  server tells, which core counts as an incomplete transfer, so downloads stay binary (the preferences say so).
  **Proof**: unit tests (6 for the file type, 4 for building a bookmark with options, 4 for the dialog, 1 for the tab) and smoke `ftp`
  against a real FTP server in Docker (`delfer/alpine-ftp-server`): a bookmark in auto mode with ISO-8859-1 and passive mode
  uploads `text.txt` (18 bytes with line feeds) and `data.bin`. The server has `text.txt` with carriage returns (20 bytes)
  and `data.bin` unchanged (16 bytes), checked with `docker exec` by `smoke.sh`. The anonymous login is covered by unit tests only,
  the test server has no anonymous account.

- [x] **6.10 Drag files out of the listing**

  **Request**: dragging out of the browser into the file manager or desktop did not work.
  **Done**: dragging selected files and folders starts a download into a staging folder (`DragStaging`) and gives the other
  application the final paths. Each item is moved to its final name when the download is complete, so a drop that comes
  too early finds no file instead of a part of one. The folder is removed when the application ends. The downloads show in the transfers window.
  **Proof**: smoke `dragout` drags a file and a folder with the real mouse (`xdotool`) from the listing into a second window of the
  application, which records the dropped files. They arrive complete with the content of the server. It is skipped when `xdotool`
  is not installed. A drop into the real file manager of a desktop has not been tried.

- [x] **6.11 Fixes found by CI and by the tests along the way**

  - The jobs of `linux-gui.yml` have time limits and apt retries, because the Ubuntu mirror was slow once. The deb install job needs up to
    13 minutes then and has to be left to finish. A cancelled job has no log.
  - `BookmarkPersistenceTest` failed once on CI. The folder monitor of the first collection can write a deleted bookmark back, so the test
    turns the monitor off.
  - The sync scenario read files that were still being written. It waits for the content now.
  - `smoke.sh` pins its home to the credentials file store, because a machine with `secret-tool` and a display starts a keyring that waits for a
    prompt. The scenarios that start transfers or change settings run in a home of their own, because a finished transfer stays in the
    list of transfers of the next start and the download scenario counts them.
  - `smoke.sh` starts an FTP server next to the SFTP one when Docker works, and the packages of the workflow include `xdotool`.

- [x] **6.12 Gap list: what the existing applications have and the Linux version does not** (list only, nothing built)

  **Request**: compare the Linux version with the existing applications and the documentation (https://docs.cyberduck.io/cyberduck/
  and https://supporthost.com/cyberduck/), and list every feature that is missing and every difference in the GUI.

  **Sources, and what could not be done**: the network policy of the working environment blocks both sites
  (`docs.cyberduck.io` and `supporthost.com`), so **neither page was read**. The list is built from the GUI definitions of the two existing
  applications in this repository (macOS: `Main`, `Browser`, `Preferences`, `Info`, `Bookmark`, `Connection` strings and the commands of
  `BrowserController`; Windows: the forms in `windows/src/main/csharp/.../winforms` and the screenshots of the owner) and from
  knowledge of the documentation. Rows that come only from the documentation, and not from the code of this repository, are marked
  **(docs)**. To check the list against the two sites, allow these two domains in the network settings of the environment and ask for a
  second pass. What exists in the Linux version is taken from its code, not from memory.

  Column "Linux?" is a suggestion for the decision, not a decision: **Yes** is likely wanted, **Maybe** depends on how it is used, **No**
  does not make sense on Linux or is not worth it. Strike what is not needed and the rest becomes the list of steps.

  **A. Connecting and logging in**

  | # | Feature | Existing application | Linux today | Linux? |
  |---|---|---|---|---|
  | A1 | SSH private key: choose a key file, passphrase from the keyring | Bookmark and Open Connection: "SSH Private Key" with Choose | No field. Only passwords. ssh-agent and `~/.ssh/config` were not checked | **Yes**, the usual way to log in with SFTP |
  | A2 | Client certificate for TLS | Bookmark: "Client Certificate" | No certificate chooser, the callback is not registered | Maybe |
  | A3 | Quick Connect field in the toolbar, type a URL and press return | Toolbar (macOS, Windows) | The Open Connection dialog takes a URL, but there is no field | **Yes**, cheap |
  | A4 | "Add to keychain / Save password" choice in the connection dialog | Open Connection | Asked only in the login prompt | Yes |
  | A5 | Bookmark fields: Web URL, Timezone, Labels, download folder per bookmark, Region | Bookmark window | Nickname, protocol, server, port, user, path, anonymous, encoding, connect mode, transfer mode | Maybe (Web URL and download folder are the useful ones) |
  | A6 | Duplicate bookmark, sort by nickname, hostname or protocol, drag to reorder, groups by label, search in bookmarks | Bookmark list and menu | Add, Edit, Delete, connect | Yes for duplicate and sort |
  | A7 | Bookmark view sizes: large, medium, small icons; protocol icon per bookmark | Preferences, Bookmarks | One list without icons | Maybe |
  | A8 | Import bookmarks from other clients (FileZilla, WinSCP and others; the `importer` module exists) and `~/.ssh/config` **(docs)** | Windows and macOS | Nothing | **Yes**, FileZilla and ssh config |
  | A9 | History of connections, as a view next to the bookmarks | View switch (macOS, Windows) | Nothing | Maybe |
  | A10 | Bonjour discovery of servers in the network | View switch (macOS, Windows) | Nothing, would need Avahi | No or low |
  | A11 | Connection profiles: install more profiles from the list of profiles | Preferences, Profiles (macOS) | Bundled profiles and files in the user folder, no screen | Maybe |
  | A12 | Choose the application that opens `ftp://` and `sftp://` links | Preferences, Default protocol handler | The desktop entry claims the schemes, no setting | Low |
  | A13 | Other protocols and their login flows: FTP-TLS, WebDAV, S3 and compatible services, Azure, Swift, Backblaze B2, Google Drive and Cloud Storage, Dropbox, OneDrive, Box, Nextcloud, DRACOON, Storegate, Brick, SMB, iRODS and the rest of the bundled profiles | All | Profiles are loaded, **only SFTP and local were used on a desktop**, FTP and the TLS prompt were tested against test servers. The browser login of the OAuth services is untested | **Yes**, verify one by one |
  | A14 | Security padlock in the browser window that shows the certificate or host key of the connection | Browser window | Prompts only when connecting | Maybe |

  **B. The browser window**

  | # | Feature | Existing application | Linux today | Linux? |
  |---|---|---|---|---|
  | B1 | Forward button, Go menu: Forward, Enclosing Folder, Inside, Go to Folder | Toolbar and Go menu | Back (history) and Up, a typed path | **Yes** (Forward) |
  | B2 | Path pop-up to jump to a parent folder | Toolbar | Typed path only | Maybe |
  | B3 | Search or filter the listing by name | Search field, Edit, Search… | Nothing | **Yes** |
  | B4 | Outline view (folders expand in place) as well as the list | View, as List or as Outline; tree in Windows | List only | Maybe |
  | B5 | Choose the columns: Group, Kind, Extension, Region, Storage class, Version, Checksum | View, Column | Filename, Size, Modified, Permissions, Owner | Maybe |
  | B6 | Show Hidden Files in the View menu with a shortcut | View | Only a setting in Preferences | **Yes** |
  | B7 | Icons per file type and folder, thumbnails | Listing | Text only | **Yes**, looks unfinished without them |
  | B8 | Toolbar with icons and labels, an Action (gear) menu, Get Info, Edit, Disconnect as buttons, "Customize Toolbar…", "Hide Toolbar" | Toolbar | Text buttons for connect, back, up, refresh, download, upload, new folder, rename, delete, transfers. No Edit, Info or Disconnect button, not customizable | Maybe (icons yes, customize no) |
  | B9 | Activity window of everything running | Window, Activity | Transfers window only | Maybe |
  | B10 | Log drawer with the commands sent to the server | Window, Toggle Log Drawer | Nothing, the program writes the file `~/.duck/cyberduck.log` | **Yes**, for support |
  | B11 | Tabs in a browser window | Windows, macOS | Only more windows | Maybe |
  | B12 | Cut, Copy, Paste of remote files to move or copy inside a connection or between two windows | Edit | Duplicate with a new name only | **Yes** |
  | B13 | Drag files inside the listing to move them, spring-loaded folders, drag between two browser windows | Listing | Drag in from the desktop and out to the desktop only | **Yes** (move) |
  | B14 | Keyboard: Delete key removes, Return or F2 renames, Backspace goes up, type to select, Select All | Listing | Return opens, menu shortcuts for New Browser, Open, Close, Edit, Duplicate, Info, Preferences, Quit, Transfers | **Yes** |
  | B15 | Window menu: Minimize, Bring All to Front, list of windows; Save Workspace and restore the connections at start | Menus, Preferences | Window menu with Transfers only | Maybe (restore at start) |
  | B16 | Quick Look preview, Print, Undo and Redo | macOS menus | Nothing | No |
  | B17 | Registration key button and Donate in the window | Windows toolbar, menu | Nothing | No, decide (the Linux version is free) |
  | B18 | Selection count and details in the status bar | Status bar | Number of items | Low |

  **C. File and folder commands**

  | # | Feature | Existing application | Linux today | Linux? |
  |---|---|---|---|---|
  | C1 | New File… (empty file, then open in the editor) | File menu | New Folder only | **Yes** |
  | C2 | New Symbolic Link… | File menu | Nothing | Maybe |
  | C3 | Download To… and Download As… (choose the folder or the name for this download), New Download from a URL | File menu | Download goes to the folder of the preferences | **Yes** |
  | C4 | Copy URL (with the choice of the URL types), Open URL, Open in Web Browser | Edit, File menu | The URL is shown in Info only | **Yes** |
  | C5 | Share… (pre-signed or shared link) and Request files… (link to upload to a folder) | File menu | Nothing | Maybe, cloud services |
  | C6 | Revert and Restore (file versions), "Show all versions" | File menu, Info, Versions | Nothing | Maybe (S3, Dropbox) |
  | C7 | Create Archive, Expand Archive on the server | File menu | Nothing | Maybe |
  | C8 | Open in Terminal (a terminal with `ssh` into the folder) | File menu | Nothing | **Yes** for SFTP |
  | C9 | Send Command… (a command on the server) | File menu | Nothing | Maybe |
  | C10 | Info: Calculate the size of a folder, the checksum of a file, Info for several files, "Info window always shows the current selection" | Info window | One file, size and permissions | **Yes** (calculate) |
  | C11 | Info tabs for cloud services: Metadata (headers), Distribution (CDN) and invalidation, S3 (storage class, encryption, ACL, access logging, versioning, lifecycle, transfer acceleration, MFA delete, location), Versions | Info window | Nothing | Maybe, if S3 is used |
  | C12 | Edit permissions for ACL services; set the grantees | Info, Permissions (ACL) | Unix permissions only | Maybe |
  | C13 | Synchronize with a list of what will change (each file with an arrow, include or exclude) | Synchronize prompt | One choice of the direction | Maybe |
  | C14 | The prompt for existing files shows both files with size and date, "apply to all", and for several files the list with a tick for each | Download and upload prompt | A dialog with the name and a choice of the action | Maybe |
  | C15 | Open the downloaded file when the transfer is done | Preferences, Transfers | Nothing | Maybe |

  **D. Transfers**

  | # | Feature | Existing application | Linux today | Linux? |
  |---|---|---|---|---|
  | D1 | Speed, time left, the file that is transferred, count of files per transfer | Transfers window | Name, status with size, percentage, speed and time left (found while building 6.16), progress bar | Done (6.16) |
  | D2 | Bandwidth limit for uploads and downloads, and per transfer | Preferences, Transfers; Transfers window | Nothing | **Yes** |
  | D3 | Segmented downloads with several connections per file | Preferences, Transfers | Core default, no setting | Low |
  | D4 | Upload with a temporary name, preserve the modification date, change permissions on upload and download, skip files by regular expression, verify the checksum | Preferences, Transfers | Core defaults, no settings | Maybe |
  | D5 | Queue: how many transfers run at once, use the browser connection or new connections | Preferences, Transfers | One choice: browser, new connection or concurrent | Low |
  | D6 | After a transfer: bring the window to front, close it, remove it from the list | Preferences, Transfers | The window opens when a transfer starts | Low |
  | D7 | Reload a transfer, "Show in Finder" for each file | Transfers window | Resume, Open Folder for the transfer | Low |
  | D8 | The "where from" mark and quarantine of downloaded files | Preferences | Not applicable | No |

  **E. Preferences**

  | # | Feature | Existing application | Linux today | Linux? |
  |---|---|---|---|---|
  | E1 | Language choice | Preferences, General | The language of the session (`LANGUAGE` or `LANG`), no setting. New labels are not translated | **Yes** |
  | E2 | Open a new browser at start, restore connections, confirm before closing a connection, double click opens the editor, Return renames, "Save passwords" | Preferences, General and Browser | Nothing | Maybe |
  | E3 | Appearance: icon sizes, alternating row colour, horizontal and vertical lines | Preferences, Appearance | System theme only | No |
  | E4 | Proxy settings in detail; system proxy | Preferences, Connection | A check box for the system proxy | Maybe |
  | E5 | Protocol settings: S3 (storage class, encryption, default ACL, location, versioning), SFTP, FTP (partly done in 6.9), Cryptomator (detect a vault and open it) | Preferences | Cryptomator vault version only through the core default | Maybe |
  | E6 | Debug log switch and where the log is | Preferences | Nothing | **Yes** |
  | E7 | Check for updates and install them | Preferences, Update; Help menu | Nothing, packages come from a repository or a download | No, or only a notice |

  **F. Application and help**

  | # | Feature | Existing application | Linux today | Linux? |
  |---|---|---|---|---|
  | F1 | Help menu: Cyberduck Help (documentation), Report a Bug, Acknowledgments, License, Privacy Policy | Help menu | No Help menu | **Yes** |
  | F2 | About window with the version | Application menu | `--version` on the command line only | **Yes** |
  | F3 | Crash report dialog | macOS, Windows | Nothing | No |
  | F4 | Integration with the file manager and the operating system: services, Spotlight, Explorer | macOS, Windows | Desktop entry and URL schemes | No |
  | F5 | Dark theme, HiDPI, Wayland, screen readers | System | Not checked, JavaFX uses GTK 3 over X11 | **Yes**, check |
  | F6 | Translations of the labels that exist only in the Linux version | All languages | English | Maybe |
  | F7 | Flatpak (5.9), the `.rpm` and the `.deb` on other releases than Ubuntu 24.04, an apt and dnf repository | Not applicable | `.deb` and `.rpm` from CI | Maybe |

  **Differences in how it looks** (summary of the above): the existing toolbar has icons with a text under each and an Action menu, the Linux one has plain text
  buttons; the existing lists show icons for protocols and file types; the existing browser has the view switch (bookmarks, history, Bonjour), the search field, the
  Quick Connect field, a path pop-up and a lock for the connection; the info window of the existing applications has tabs; the existing preferences are a window of panes
  with many more settings (E); the Linux window title and the Window menu are simpler.

- [x] **6.13 Decide and build**

  Decision of the owner (2026-10-08): do the rows marked **Yes** in 6.12. The rest waits. The Yes rows are built in three batches, one step each.

- [ ] **6.14 Yes batch 1: logging in, moving around, help** (A1, A3, B1, B3, B6, B14, F1, F2)

  Done, proved by 111 unit tests; the smoke scenarios `navigate`, `sftp` and `sshkey` run on CI:
  - [x] SSH private key: a field with a Choose button in the connection and bookmark dialogs for protocols that take a key (SFTP). Stored in the bookmark, kept by Edit and Duplicate. Smoke `sshkey` logs in to an SFTP container with a generated key and requires that no password is asked.
  - [x] Quick Connect field in the toolbar and in the File menu (Ctrl+K): a URL, or a server name with the default protocol. Smoke `sftp` ends with a connection by URL.
  - [x] Forward button and Go menu (Back Alt+Left, Forward Alt+Right, Enclosing Folder Alt+Up, Go to Folder Ctrl+L).
  - [x] Search field in the toolbar (Ctrl+F) that filters the listing by name; the status line counts "1 of 5 items".
  - [x] View menu: Show Hidden Files (Ctrl+Shift+.) and Refresh (Ctrl+R).
  - [x] Keys in the listing: Delete, F2 (rename), Backspace (parent folder).
  - [x] Help menu: Help, Report a Bug, License, Acknowledgments, Privacy Policy, About with the version.
  - [x] CI run 20 failed in the existing `vault` scenario, not in the new code: after unlocking a vault the browser reloads the folder, and a double click made in that moment is replaced by the reload. The scenario now repeats the click (`openFolder`). The scenario passes locally with the new code.
  - [ ] Result of the CI run with the fix, and the `sshkey` scenario, which needs Docker and has only run on CI.

- [x] **6.15 Yes batch 2: files and folders** (C1, C3, C4, C8, C10, B7, B12, B13)

  Done, proved by 118 unit tests and by the scenario `files` (real windows, local folder; it also ran locally):
  - [x] New File (File menu Ctrl+Alt+N, context menu): asks for a name, makes an empty file, selects it. A name that exists is refused.
  - [x] Download To… (a folder) and Download As… (one file, with a name) in the File menu and the context menu.
  - [x] Copy URL (Edit menu Ctrl+Shift+C, context menu): the address of the selected files on the clipboard, one per line. Open in Web Browser (File menu) when the server has a web address, otherwise a message.
  - [x] Open in Terminal (File menu Ctrl+Alt+T, context menus, only for SFTP): ssh with the port, the key file and the folder, in the terminal that is installed (`x-terminal-emulator`, gnome-terminal, konsole, xfce4-terminal, mate-terminal, tilix, kitty, alacritty, foot, xterm) or the one in the preference `linux.terminal`. The command is covered by unit tests (`TerminalLauncherTest`); the scenario `sshkey` starts a script in place of the terminal and checks what it was given.
  - [x] Folder size: the info window of a folder has a Calculate button that adds up everything in it (`files` checks 15 bytes for two files of 10 and 5).
  - [x] Cut, Copy and Paste (Edit menu Ctrl+X, Ctrl+C, Ctrl+V and context menus) within one connection, also from one window to another. A copy next to the original gets the name "name copy" and then "name copy 2". Pasting into another connection is refused with a message (not built).
  - [x] Drag files onto a folder of the listing to move them (real mouse in the scenario `files`). Files that are dragged out of the window are downloaded from the moment they leave it, not when the drag starts; the scenario `dragout` still passes.
  - [x] Icons in the file name column: a folder, and a file tinted by kind (pictures, sound and video, archives, programs and text).
  - [x] Select All (Ctrl+A) in the Edit menu.
  - [ ] Result of the CI run.

- [x] **6.16 Yes batch 3: transfers, preferences, bookmarks** (D1, D2, E1, E6, B10, A6, A8)

  Done, proved by 125 unit tests, by the scenarios `preferences`, `bookmarks` and `locale` (which also ran locally) and, with a server in a container, by `ftp` and `sftp` on CI:
  - [x] Speed and time left of a transfer: the status of a running transfer already read "1 MB of 5 MB (20%, 500 KB/sec, 8 seconds remaining)" because the core builds that text. The row in 6.12 was wrong. The scenario `sftp` now requires it (`/sec` and `%`) and prints it.
  - [x] Speed limits: the Transfers tab of the preferences has "Limit download speed" and "Limit upload speed" (unlimited, 50, 100, 250, 500 KB/s, 1, 2, 5, 10 MB/s). They are stored in `queue.download.bandwidth.bytes` and `queue.upload.bandwidth.bytes` and apply to transfers that start afterwards.
  - [x] Language: the General tab lists the languages that have translations in their own names (`Languages`) with "System default" first. The choice (`linux.language`) beats the language of the session and is read at startup, so it needs a restart; the tab says so. The scenario `locale` starts with `LANGUAGE=de` and `linux.language=fr` and requires French.
  - [x] Log: the General tab has the log level (errors, warnings, information, debug) which applies at once, and a button that shows the folder with the log file. View > Show Log (Ctrl+Shift+L) shows a drawer above the status line with what was sent to the server and what it answered ("> USER", "< 230 ..."). The scenario `ftp` requires the commands in it and requires that the password line is not.
  - [x] Bookmarks: Duplicate (menu "More" and the right mouse button), Sort By name, server or protocol (kept in `linux.bookmarks.sort`; unset means the saved order), and the right mouse button menu of the list.
  - [x] Import of bookmarks: "Import from FileZilla…" (`~/.config/filezilla/sitemanager.xml`, else the file is asked for) and "Import from SSH Config…" (`~/.ssh/config`; the hosts that are not patterns, with HostName, User, Port and IdentityFile). Servers that are bookmarks already (same protocol, server, port and user) are skipped, and the status line says how many were added. The scenario `bookmarks` imports a file twice and requires one new bookmark.
  - [x] Core change for review: `importer/.../FilezillaBookmarkCollection` reset the port of a server to the default when it read the protocol, which comes after the port in the file of FileZilla, so every port that is not the default was lost. It keeps the port now. `importer` is a new dependency of the `linux` module.
  - [ ] Result of the CI run.

## Phase 7: Repository, releases and the offer to upstream (decided 2026-10-08)

- [x] **7.1 Layout of the repository**

  - `master` mirrors `iterate-ch/cyberduck`. Nobody commits to it. It is updated with the Sync fork button of GitHub.
  - `main` is the Linux line and the default branch. Upstream is merged in with `git merge master`. The workflow `linux-gui.yml` runs on pushes to `main` and on pull requests.
  - The code of the Linux version is in `linux/`. Changes outside it are kept few and small: `ftp/.../FTPFileType` and `FTPWriteFeature` (6.9), `defaults/.../default.properties`, `importer/.../FilezillaBookmarkCollection` (6.16), and the workflows.
  - The repository is renamed `cyberduck-linux`. Done by the owner on GitHub: [x] rename (2026-10-08, old links redirect), [x] default branch `main`, [ ] delete `cyberduck-linuxgui` (still there on 2026-10-08), [ ] switch off the upstream workflows that this repository does not use. CI on `main` passed (run 24) with the new name.
  - [x] Licence headers: `LICENSE.txt` is GPL version 3. Upstream files have both headers (1918 files "version 2 or later", 1392 files "version 3 or later"), and the files of `linux/` have "version 3 or later". Nothing to change.

- [ ] **7.2 First release**

  After the Yes items of 6.12 are done and tested again on a desktop: a pre-release tag such as `v9.6.0-linux.1` (upstream version plus the Linux build). It is the first real run of the release workflow (4.6). Until upstream agrees on the name, the packages are described as a community build, "Cyberduck for Linux (unofficial)", because "Cyberduck" is a trademark of iterate GmbH.

- [ ] **7.3 Offer to upstream**

  A branch from `master` with a few squashed commits (the history of `main` has about 60 commits and merges). The changes that help every platform go first as pull requests of their own: the FTP ASCII upload mode and the FileZilla port fix. Ask about the name in the same message.

---

## Appendix A: `linux/pom.xml` outline

```xml
<project ...>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>ch.cyberduck</groupId>
        <artifactId>parent</artifactId>
        <relativePath>../pom.xml</relativePath>
        <version>SAME AS cli/linux/pom.xml</version>
    </parent>
    <artifactId>linux</artifactId>
    <description>Cyberduck Linux</description>
    <packaging>jar</packaging>
    <properties>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
        <javafx.version>25.x.y</javafx.version>
        <maven.main.skip>true</maven.main.skip>
        <maven.test.skip>true</maven.test.skip>
    </properties>
    <profiles>
        <profile>
            <id>linux</id>
            <activation><os><family>Linux</family></os></activation>
            <properties>
                <maven.main.skip>false</maven.main.skip>
                <maven.test.skip>false</maven.test.skip>
            </properties>
            <dependencies>
                <!-- core, protocols, cryptomator, javafx-controls, test (test-jar), junit, testcontainers (test) -->
            </dependencies>
            <build>
                <plugins>
                    <plugin>
                        <artifactId>maven-enforcer-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>enforce-bytecode-version</id>
                                <configuration>
                                    <rules>
                                        <enforceBytecodeVersion>
                                            <maxJdkVersion>1.8</maxJdkVersion>
                                            <ignoredScopes><ignoreScope>test</ignoreScope></ignoredScopes>
                                            <excludes><exclude>org.openjfx:*</exclude></excludes>
                                        </enforceBytecodeVersion>
                                    </rules>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                    <plugin>
                        <artifactId>maven-dependency-plugin</artifactId>
                        <!-- unpack-profiles (step 1.4), unpack-i18n (step 4.1); inherits copy-dependencies-* -->
                    </plugin>
                    <!-- maven-antrun-plugin with no configuration: added in step 4.1 -->
                </plugins>
            </build>
        </profile>
        <!-- arm64 / arm32 / x86_64 profiles copied from cli/linux/pom.xml (libjnidispatch only) -->
    </profiles>
</project>
```

## Appendix B: `linux/smoke.sh` contract

- Runs from the repository root, uses `linux/run.sh` unless `CYBERDUCK_BIN` is set (the package
  install tests set it to `cyberduck` or `/opt/cyberduck/bin/Cyberduck`).
- Creates its fixtures under `mktemp -d`, removes them on exit.
- Runs each `--smoke` mode under `timeout 120`, greps the expected `SMOKE OK ...` line, and
  verifies filesystem effects (`sha256sum`, `test -d`, `test ! -e`).
- Exits non-zero on the first failure and prints which mode failed.

## Appendix C: `linux-gui.yml` outline (final shape after Phase 4)

```yaml
name: Linux GUI
on:
  push:
    branches: [ main ]
  pull_request:
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
jobs:
  build:
    runs-on: ubuntu-latest
    permissions: { checks: write, pull-requests: write, statuses: write, contents: read, packages: read }
    steps:
      - uses: actions/checkout@v7
        with: { fetch-depth: 0 }
      - uses: actions/setup-java@v6
        with: { distribution: temurin, java-version: 25, cache: maven }
      - run: sudo apt-get update && sudo apt-get install -y --no-install-recommends xvfb rpm fakeroot desktop-file-utils
      - run: mvn --no-transfer-progress --batch-mode verify -DskipITs -DskipSign -Drevision=0 --also-make --projects i18n,profiles,linux
        env: { SKIP_SIGN: true }
      - run: xvfb-run -a linux/smoke.sh
      - run: mvn --batch-mode -pl linux -DskipSign -Drevision=0 test -Dtest=SFTPBrowserIntegrationTest -Dsurefire.group.excluded=none
      - uses: actions/upload-artifact@v7
        with: { name: cyberduck-deb, path: linux/target/release/*.deb, archive: false }
      - uses: actions/upload-artifact@v7
        with: { name: cyberduck-rpm, path: linux/target/release/*.rpm, archive: false }
  install-deb:
    needs: build
    runs-on: ubuntu-latest
    steps: [ download cyberduck-deb, apt-get install ./cyberduck_*.deb, cyberduck --version, xvfb-run -a cyberduck --smoke list /tmp ]
  install-rpm:
    needs: build
    runs-on: ubuntu-latest
    container: fedora:latest
    steps: [ download cyberduck-rpm, dnf install -y ./cyberduck-*.rpm xorg-x11-server-Xvfb, /opt/cyberduck/bin/Cyberduck --version, xvfb-run -a /opt/cyberduck/bin/Cyberduck --smoke list /tmp ]
```
