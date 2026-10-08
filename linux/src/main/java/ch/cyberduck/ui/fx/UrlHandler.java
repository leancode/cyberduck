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

import ch.cyberduck.core.oauth.OAuth2TokenListenerRegistry;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Handles the URLs that the desktop passes to the application: links to servers such as {@code sftp://example.net}
 * and the answer of a web browser after logging in to a cloud service such as {@code io.cyberduck:oauth?code=…&state=…}.
 */
final class UrlHandler {
    private static final Logger log = LogManager.getLogger(UrlHandler.class);

    private static final Pattern SERVER = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://.+");
    private static final Pattern CALLBACK = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:(//)?oauth[/]?\\?.+", Pattern.CASE_INSENSITIVE);

    private UrlHandler() {
        //
    }

    /**
     * @return True for a link to a server
     */
    static boolean isServer(final String url) {
        return null != url && !isCallback(url) && SERVER.matcher(url).matches();
    }

    /**
     * @return True for the answer of a web browser to a login. This has the form of {@code scheme:oauth?code=…}
     * or {@code scheme://oauth?code=…} with the scheme that the service was told to redirect to.
     */
    static boolean isCallback(final String url) {
        return null != url && CALLBACK.matcher(url).matches();
    }

    /**
     * @return Decoded parameters of the query
     */
    static Map<String, String> parameters(final String url) {
        final Map<String, String> parameters = new LinkedHashMap<>();
        final String query = StringUtils.substringBefore(StringUtils.substringAfter(url, "?"), "#");
        for(String pair : StringUtils.split(query, '&')) {
            final String name = StringUtils.substringBefore(pair, "=");
            if(StringUtils.isEmpty(name)) {
                continue;
            }
            parameters.putIfAbsent(decode(name), decode(StringUtils.substringAfter(pair, "=")));
        }
        return parameters;
    }

    private static String decode(final String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        catch(IllegalArgumentException e) {
            return value;
        }
    }

    /**
     * Pass the answer of the web browser to the connection that waits for it
     *
     * @return False if no connection is waiting for this answer
     */
    static boolean callback(final String url) {
        final Map<String, String> parameters = parameters(url);
        if(parameters.containsKey("error")) {
            log.warn("Login was declined with {}", parameters.get("error"));
        }
        // Without a code the login is cancelled
        return OAuth2TokenListenerRegistry.get().notify(
            parameters.getOrDefault("state", StringUtils.EMPTY), parameters.getOrDefault("code", StringUtils.EMPTY));
    }

    /**
     * Use on the JavaFX application thread.
     *
     * @param url Link to a server or a callback from a web browser
     */
    static void handle(final String url) {
        if(isCallback(url)) {
            log.debug("Handle callback {}", StringUtils.substringBefore(url, "?"));
            if(!callback(url)) {
                log.warn("Nothing waits for the callback from the web browser");
            }
            front(MainController.get().getBrowsers().stream().findFirst().orElse(null));
        }
        else if(isServer(url)) {
            log.debug("Handle link {}", url);
            // A window that is free, so that the connection already open stays
            BrowserController browser = MainController.get().getBrowsers().stream().filter(b -> !b.isMounted()).findFirst().orElse(null);
            if(null == browser) {
                browser = MainController.get().newBrowser(null);
            }
            browser.open(url);
            front(browser);
        }
        else {
            log.warn("Ignore {}", url);
        }
    }

    /**
     * Bring the window to the front and show it again if it was minimized
     */
    static void front(final BrowserController browser) {
        if(null == browser) {
            return;
        }
        browser.getStage().setIconified(false);
        browser.getStage().show();
        browser.getStage().toFront();
        browser.getStage().requestFocus();
    }
}
