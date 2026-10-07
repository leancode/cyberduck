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

import ch.cyberduck.core.LocaleFactory;
import ch.cyberduck.core.notification.NotificationService;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Shows desktop notifications with <code>notify-send</code> of libnotify, which talks to the notification daemon of the
 * desktop. Does nothing when the tool is not installed.
 */
public class NotifySendNotificationService implements NotificationService {
    private static final Logger log = LogManager.getLogger(NotifySendNotificationService.class);

    private static final long TIMEOUT_SECONDS = 10;

    private final String executable;
    private boolean missing;

    public NotifySendNotificationService() {
        this("notify-send");
    }

    public NotifySendNotificationService(final String executable) {
        this.executable = executable;
    }

    @Override
    public NotificationService setup() {
        return this;
    }

    @Override
    public void unregister() {
        //
    }

    @Override
    public void addListener(final Listener listener) {
        // Notifications have no action to call back
    }

    @Override
    public void notify(final String group, final String identifier, final String title, final String description) {
        this.send(identifier, LocaleFactory.localizedString(title, "Status"), StringUtils.isBlank(group) ? description : group.equals(description) ? description : String.format("%s\n%s", group, description));
    }

    @Override
    public void notify(final String group, final String identifier, final String title, final String description, final String action) {
        this.notify(group, identifier, title, description);
    }

    private synchronized void send(final String identifier, final String summary, final String body) {
        if(missing) {
            return;
        }
        final List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("--app-name=Cyberduck");
        if(StringUtils.isNotBlank(identifier)) {
            // Replace the notification of the same transfer instead of stacking them
            command.add(String.format("--hint=string:x-canonical-private-synchronous:%s", identifier));
        }
        command.add("--");
        command.add(summary);
        command.add(StringUtils.defaultString(body));
        try {
            final Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            process.getOutputStream().close();
            if(!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("Timeout running {}", executable);
            }
            else if(process.exitValue() != 0) {
                log.warn("Failure {} showing notification {}", process.exitValue(), summary);
            }
        }
        catch(IOException e) {
            log.warn("Cannot run {}. {}", executable, e.getMessage());
            // Do not try again for every transfer
            missing = true;
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
