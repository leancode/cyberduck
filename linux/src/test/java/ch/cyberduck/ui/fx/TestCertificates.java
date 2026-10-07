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

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public final class TestCertificates {

    private TestCertificates() {
        //
    }

    /**
     * @param days Validity in days from now. Negative values create an expired certificate.
     */
    public static X509Certificate selfSigned(final String commonName, final int days) throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        final KeyPair pair = generator.generateKeyPair();
        final X500Name name = new X500Name(String.format("CN=%s", commonName));
        final long now = System.currentTimeMillis();
        final Date from = new Date(now - TimeUnit.DAYS.toMillis(Math.abs(days) + 1));
        final Date until = new Date(now + TimeUnit.DAYS.toMillis(days));
        return new JcaX509CertificateConverter().getCertificate(
            new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), from, until, name, pair.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
    }
}
