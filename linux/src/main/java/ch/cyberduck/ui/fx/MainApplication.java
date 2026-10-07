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

import ch.cyberduck.core.Protocol;
import ch.cyberduck.core.ProtocolFactory;

import java.util.Arrays;
import java.util.List;

import javafx.application.Application;

/**
 * Plain launcher. Does not extend {@link Application} so that JavaFX can be started from the class path.
 */
public final class MainApplication {

    private MainApplication() {
        //
    }

    public static void main(final String... args) {
        final List<String> arguments = Arrays.asList(args);
        Bootstrap.initialize();
        if(arguments.contains("--version")) {
            System.out.printf("Cyberduck %s%n", Version.get());
            return;
        }
        if(arguments.contains("--list-protocols")) {
            for(Protocol protocol : ProtocolFactory.get().find()) {
                System.out.printf("%s\t%s%n", protocol.getIdentifier(), protocol.getDescription());
            }
            return;
        }
        final int smoke = arguments.indexOf("--smoke");
        if(smoke >= 0) {
            // Exit explicitly because the core keeps non-daemon threads alive
            System.exit(Smoke.run(arguments.subList(smoke + 1, arguments.size())));
        }
        Application.launch(CyberduckApplication.class, args);
    }
}
