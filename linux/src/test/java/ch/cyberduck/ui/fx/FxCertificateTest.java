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

import ch.cyberduck.core.CertificateTrustCallback;
import ch.cyberduck.core.exception.ConnectionCanceledException;

import org.junit.Test;

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FxCertificateTest {

    @Test
    public void testTrustAccepted() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        new FxCertificateTrustCallback(dialogs).prompt("example.net", Collections.singletonList(TestCertificates.selfSigned("example.net", 30)));
        assertTrue(dialogs.titles.get(0).contains("example.net"));
    }

    @Test(expected = ConnectionCanceledException.class)
    public void testTrustDenied() throws Exception {
        final FakeDialogService dialogs = new FakeDialogService();
        dialogs.confirmation = DialogService.Confirmation.NO;
        new FxCertificateTrustCallback(dialogs).prompt("example.net", Collections.singletonList(TestCertificates.selfSigned("example.net", 30)));
    }

    @Test(expected = ConnectionCanceledException.class)
    public void testNoCertificate() throws Exception {
        new FxCertificateTrustCallback(new FakeDialogService()).prompt("example.net", Collections.emptyList());
    }

    @Test
    public void testDescription() throws Exception {
        final String text = FxCertificateTrustCallback.describe("example.net", TestCertificates.selfSigned("example.net", 30));
        assertTrue(text.contains("CN=example.net"));
        assertTrue(text.contains("SHA-256 fingerprint: "));
        assertFalse(text.contains("expired"));
        final String fingerprint = text.substring(text.indexOf("SHA-256 fingerprint: ") + "SHA-256 fingerprint: ".length()).trim();
        // 32 bytes as hex pairs separated by colons
        assertEquals(32 * 3 - 1, fingerprint.length());
        assertTrue(fingerprint.matches("([0-9A-F]{2}:){31}[0-9A-F]{2}"));
    }

    @Test
    public void testDescriptionExpired() throws Exception {
        assertTrue(FxCertificateTrustCallback.describe("example.net", TestCertificates.selfSigned("example.net", -2)).contains("expired"));
    }

    @Test
    public void testStoreTrustsWhenUserContinues() throws Exception {
        final List<X509Certificate> chain = Collections.singletonList(TestCertificates.selfSigned("example.net", 30));
        assertTrue(new FxCertificateStore().verify((hostname, certificates) -> {
        }, "example.net", chain));
    }

    @Test
    public void testStoreDoesNotTrustWhenUserCancels() throws Exception {
        final List<X509Certificate> chain = Collections.singletonList(TestCertificates.selfSigned("example.net", 30));
        final CertificateTrustCallback cancel = (hostname, certificates) -> {
            throw new ConnectionCanceledException();
        };
        assertFalse(new FxCertificateStore().verify(cancel, "example.net", chain));
    }

    @Test
    public void testStoreDoesNotTrustEmptyChain() {
        assertFalse(new FxCertificateStore().verify((hostname, certificates) -> {
        }, "example.net", Collections.emptyList()));
    }
}
