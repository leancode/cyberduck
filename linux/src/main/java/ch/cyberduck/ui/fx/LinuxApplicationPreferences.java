package ch.cyberduck.ui.fx;

/*
 * Copyright (c) 2002-2026 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

import ch.cyberduck.core.Local;
import ch.cyberduck.core.cryptomator.DefaultVaultProvider;
import ch.cyberduck.core.editor.DefaultEditorFactory;
import ch.cyberduck.core.i18n.RegexLocale;
import ch.cyberduck.core.local.DefaultSymlinkFeature;
import ch.cyberduck.core.preferences.DefaultPreferences;
import ch.cyberduck.core.preferences.UserHomeSupportDirectoryFinder;
import ch.cyberduck.core.proxy.EnvironmentVariableProxyFinder;
import ch.cyberduck.core.transfer.Transfer;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Preferences of the Linux desktop application persisted as a properties file in the application support directory.
 * Only values changed by the user are stored. Defaults are kept in memory.
 */
public class LinuxApplicationPreferences extends DefaultPreferences {
    private static final Logger log = LogManager.getLogger(LinuxApplicationPreferences.class);

    private final Path file;
    private final Properties store = new Properties();

    public LinuxApplicationPreferences() {
        this(defaultFile());
    }

    /**
     * @param file Properties file to read and write
     */
    public LinuxApplicationPreferences(final Path file) {
        this.file = file;
    }

    /**
     * The file is resolved without the preferences factories because preferences are loaded before factories are
     * registered. It is the same folder as returned by {@link UserHomeSupportDirectoryFinder}.
     *
     * @return {@code ~/.duck/cyberduck.properties}
     */
    public static Path defaultFile() {
        return Paths.get(userHome(), ".duck", "cyberduck.properties");
    }

    /**
     * Java ignores the {@code HOME} environment variable and reads the home folder from the user database. Prefer the
     * variable because it is how a desktop session, a container or a test run selects the home folder.
     *
     * @return Home folder of the user
     */
    public static String userHome() {
        final String home = System.getenv("HOME");
        if(StringUtils.isNotBlank(home) && Files.isDirectory(Paths.get(home))) {
            return home;
        }
        return System.getProperty("user.home");
    }

    public Path getFile() {
        return file;
    }

    @Override
    public void setProperty(final String property, final String v) {
        store.setProperty(property, v);
    }

    @Override
    public void deleteProperty(final String property) {
        store.remove(property);
    }

    @Override
    public String getProperty(final String property) {
        final String value = store.getProperty(property);
        if(null == value) {
            return this.getDefault(property);
        }
        return value;
    }

    @Override
    public void load() {
        store.clear();
        if(Files.isReadable(file)) {
            try(Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                store.load(reader);
                log.debug("Loaded {} preferences from {}", store.size(), file);
            }
            catch(IOException e) {
                log.warn("Failure reading preferences from {}. {}", file, e.getMessage());
            }
        }
    }

    @Override
    public void save() {
        try {
            Files.createDirectories(file.getParent());
            // Write to temporary file first to never leave a truncated preferences file behind
            final Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
            try(Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                store.store(writer, null);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.debug("Saved {} preferences to {}", store.size(), file);
        }
        catch(IOException e) {
            log.warn("Failure writing preferences to {}. {}", file, e.getMessage());
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected void setDefaults() {
        super.setDefaults();

        this.setDefault("local.user.home", userHome());
        this.setDefault("queue.download.folder", Paths.get(userHome(), "Downloads").toString());
        // Same folder name as the bundled profiles unpacked next to the application resources
        this.setDefault("profiles.folder.name", "profiles");
        // Problems of this version are reported to the repository of this version. The text is the version of the program.
        this.setDefault("website.bug", "https://github.com/leancode/cyberduck-linux/issues/new?body=Version%20{0}");
        this.setDefault("website.linux", "https://github.com/leancode/cyberduck-linux");
        // Log in like ssh does without being told: with the keys of the ssh agent (SSH_AUTH_SOCK), with the settings of
        // ~/.ssh/config, and with the default key files. The core looks for one RSA and one DSA key file, so these two
        // places hold the keys that are in use today: the Ed25519 key first, then the RSA key.
        this.setDefault("ssh.authentication.agent.enable", String.valueOf(true));
        this.setDefault("ssh.authentication.publickey.default.enable", String.valueOf(true));
        this.setDefault("ssh.authentication.publickey.default.rsa", "~/.ssh/id_ed25519");
        this.setDefault("ssh.authentication.publickey.default.dsa", "~/.ssh/id_rsa");
        this.setDefault("connection.ssl.securerandom.algorithm", "NativePRNGNonBlocking");
    }

    @Override
    protected void setFactories() {
        super.setFactories();

        this.setDefault("factory.local.class", Local.class.getName());
        this.setDefault("factory.supportdirectoryfinder.class", UserHomeSupportDirectoryFinder.class.getName());
        this.setDefault("factory.localsupportdirectoryfinder.class", UserHomeSupportDirectoryFinder.class.getName());
        this.setDefault("factory.applicationresourcesfinder.class", LinuxApplicationResourcesFinder.class.getName());
        this.setDefault("factory.locale.class", RegexLocale.class.getName());
        this.setDefault("factory.browserlauncher.class", XdgOpenBrowserLauncher.class.getName());
        this.setDefault("factory.applicationlauncher.class", LinuxApplicationLauncher.class.getName());
        this.setDefault("factory.applicationfinder.class", LinuxApplicationFinder.class.getName());
        this.setDefault("factory.reveal.class", XdgOpenRevealService.class.getName());
        this.setDefault("factory.editorfactory.class", DefaultEditorFactory.class.getName());
        this.setDefault("factory.proxy.class", EnvironmentVariableProxyFinder.class.getName());
        this.setDefault("factory.symlink.class", DefaultSymlinkFeature.class.getName());
        this.setDefault("factory.logincallback.class", FxLoginCallback.class.getName());
        this.setDefault("factory.passwordcallback.class", FxPasswordCallback.class.getName());
        this.setDefault("factory.hostkeycallback.class", FxHostKeyCallback.class.getName());
        this.setDefault("factory.certificatetrustcallback.class", FxCertificateTrustCallback.class.getName());
        this.setDefault("factory.certificatestore.class", FxCertificateStore.class.getName());
        this.setDefault("factory.alertcallback.class", FxAlertCallback.class.getName());
        this.setDefault("factory.transfererrorcallback.class", FxTransferErrorCallback.class.getName());
        for(Transfer.Type type : Transfer.Type.values()) {
            this.setDefault(String.format("factory.transferpromptcallback.%s.class", type.name()), FxTransferPrompt.class.getName());
        }
        // Most servers of the users are reached with SSH
        this.setDefault("connection.protocol.default", "sftp");
        this.setDefault("factory.vaultprovider.class", DefaultVaultProvider.class.getName());
        this.setDefault("factory.notification.class", NotifySendNotificationService.class.getName());
        // The keyring of the desktop. Falls back to the credentials file when secret-tool is not installed.
        this.setDefault("factory.passwordstore.class", SecretToolPasswordStore.class.getName());
    }

    @Override
    public List<String> applicationLocales() {
        return Collections.singletonList("en");
    }

    @Override
    public List<String> systemLocales() {
        return Collections.singletonList("en");
    }
}
