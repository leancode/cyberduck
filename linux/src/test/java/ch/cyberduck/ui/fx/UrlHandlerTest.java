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

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UrlHandlerTest {

    @Test
    public void testCallbackForms() {
        assertTrue(UrlHandler.isCallback("io.cyberduck:oauth?code=4%2Fabc&state=xyz"));
        assertTrue(UrlHandler.isCallback("x-cyberduck-action:oauth?code=abc&state=xyz"));
        assertTrue(UrlHandler.isCallback("io.cyberduck://oauth?code=abc&state=xyz"));
        assertFalse(UrlHandler.isCallback("io.cyberduck:oauth"));
        assertFalse(UrlHandler.isCallback("sftp://example.net/oauth?code=1"));
        assertFalse(UrlHandler.isCallback("sftp://example.net"));
        assertFalse(UrlHandler.isCallback(null));
    }

    @Test
    public void testServerLinks() {
        assertTrue(UrlHandler.isServer("sftp://alice@example.net/home"));
        assertTrue(UrlHandler.isServer("s3://bucket"));
        assertFalse(UrlHandler.isServer("io.cyberduck:oauth?code=abc&state=xyz"));
        assertFalse(UrlHandler.isServer("--smoke"));
        assertFalse(UrlHandler.isServer("activate"));
        assertFalse(UrlHandler.isServer(null));
    }

    @Test
    public void testParametersAreDecoded() {
        assertEquals("4/0Ab c", UrlHandler.parameters("io.cyberduck:oauth?code=4%2F0Ab+c&state=xyz&scope=a%20b#fragment").get("code"));
        assertEquals("xyz", UrlHandler.parameters("io.cyberduck:oauth?code=1&state=xyz").get("state"));
        assertEquals("a b", UrlHandler.parameters("io.cyberduck:oauth?scope=a%20b#fragment").get("scope"));
        assertTrue(UrlHandler.parameters("io.cyberduck:oauth").isEmpty());
    }

    @Test
    public void testCallbackReachesTheLoginThatWaits() {
        final AtomicReference<String> received = new AtomicReference<>();
        OAuth2TokenListenerRegistry.get().register("state-1", received::set);
        assertTrue(UrlHandler.callback("io.cyberduck:oauth?code=4%2Fabc&state=state-1"));
        assertEquals("4/abc", received.get());
        // Used once
        assertFalse(UrlHandler.callback("io.cyberduck:oauth?code=4%2Fabc&state=state-1"));
    }

    @Test
    public void testDeclinedLoginEndsTheWait() {
        final AtomicReference<String> received = new AtomicReference<>("untouched");
        OAuth2TokenListenerRegistry.get().register("state-2", received::set);
        assertTrue(UrlHandler.callback("io.cyberduck:oauth?error=access_denied&state=state-2"));
        assertEquals("", received.get());
    }

    @Test
    public void testUnknownStateIsIgnored() {
        assertFalse(UrlHandler.callback("io.cyberduck:oauth?code=abc&state=unknown"));
    }
}
