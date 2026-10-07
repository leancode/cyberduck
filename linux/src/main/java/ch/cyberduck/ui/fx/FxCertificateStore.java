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
import ch.cyberduck.core.DefaultCertificateStore;
import ch.cyberduck.core.exception.ConnectionCanceledException;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * The core asks the store only for certificates that are not trusted by the Java runtime. Always let the user decide
 * instead of accepting any certificate with a matching name.
 */
public class FxCertificateStore extends DefaultCertificateStore {

    @Override
    public boolean verify(final CertificateTrustCallback prompt, final String hostname, final List<X509Certificate> certificates) {
        if(certificates.isEmpty()) {
            return false;
        }
        try {
            prompt.prompt(hostname, certificates);
            return true;
        }
        catch(ConnectionCanceledException e) {
            return false;
        }
    }
}
