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

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;

/**
 * Ask whether to trust the certificate of a server that is not trusted by the system
 */
public class FxCertificateTrustCallback implements CertificateTrustCallback {
    private static final Logger log = LogManager.getLogger(FxCertificateTrustCallback.class);

    private final DialogService dialogs;

    /**
     * Created by the core with the controller of the window
     */
    public FxCertificateTrustCallback(final FxController controller) {
        this(new FxDialogService(controller));
    }

    FxCertificateTrustCallback(final DialogService dialogs) {
        this.dialogs = dialogs;
    }

    @Override
    public void prompt(final String hostname, final List<X509Certificate> certificates) throws ConnectionCanceledException {
        if(certificates.isEmpty()) {
            throw new ConnectionCanceledException(String.format("No certificate sent by %s", hostname));
        }
        if(!dialogs.confirm(String.format("Certificate not trusted for %s", hostname), describe(hostname, certificates.get(0)),
            "Continue", "Cancel", false).accepted()) {
            throw new ConnectionCanceledException();
        }
    }

    /**
     * Text with the details of the certificate sent by the server
     */
    static String describe(final String hostname, final X509Certificate certificate) {
        final StringBuilder text = new StringBuilder();
        text.append(String.format("The certificate sent by %s is not trusted by this computer. ", hostname));
        text.append("You might be connecting to a server that is pretending to be this server, which could put your confidential information at risk.");
        text.append("\n\n");
        text.append(String.format("Subject: %s%n", certificate.getSubjectX500Principal().getName()));
        text.append(String.format("Issuer: %s%n", certificate.getIssuerX500Principal().getName()));
        text.append(String.format("Valid from %s until %s%n", certificate.getNotBefore(), certificate.getNotAfter()));
        try {
            certificate.checkValidity();
        }
        catch(CertificateExpiredException e) {
            text.append("The certificate has expired.\n");
        }
        catch(CertificateNotYetValidException e) {
            text.append("The certificate is not yet valid.\n");
        }
        try {
            text.append(String.format("SHA-256 fingerprint: %s", fingerprint(certificate.getEncoded())));
        }
        catch(CertificateEncodingException e) {
            log.warn("Failure encoding certificate. {}", e.getMessage());
        }
        return text.toString();
    }

    /**
     * @return Hex digest with pairs separated by colons
     */
    static String fingerprint(final byte[] encoded) {
        return DigestUtils.sha256Hex(encoded).toUpperCase(Locale.ROOT).replaceAll("(..)(?!$)", "$1:");
    }
}
