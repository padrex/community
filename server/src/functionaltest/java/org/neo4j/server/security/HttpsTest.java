/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.security;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.core.MediaType;

import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.junit.Test;
import org.neo4j.server.NeoServerWithEmbeddedWebServer;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.helpers.ServerBuilder;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;

public class HttpsTest {

    @Test
    public void shouldGenerateSelfSignedCertificateIfNoCertificateIsProvided()
            throws IOException {

        File certFile = new File(Configurator.DEFAULT_WEBSERVER_HTTPS_CERT_PATH);
        File keyFile = new File(
                Configurator.DEFAULT_WEBSERVER_HTTPS_KEY_PATH);

        if (certFile.exists()) {
            certFile.delete();
        }

        if (keyFile.exists()) {
            keyFile.delete();
        }

        NeoServerWithEmbeddedWebServer server = ServerBuilder.server().build();
        server.start();
        server.stop();

        assertThat(certFile.exists(), is(true));
        assertThat(keyFile.exists(), is(true));
    }

    @Test
    public void shouldSupportSslOutOfTheBox() throws Exception {

        NeoServerWithEmbeddedWebServer server = ServerBuilder.server().build();
        server.start();

        try {

            URI uri = new URI("https://localhost:"
                    + Configurator.DEFAULT_WEBSERVER_HTTPS_PORT + "/");

            ClientRequest req = ClientRequest.create()
                    .accept(MediaType.APPLICATION_JSON).build(uri, "GET");

            Client client = getClientThatAcceptsAllSslCertificates();
            ClientResponse response = client.handle(req);

            assertThat(response.getStatus(), is(200));
        } finally {
            server.stop();
        }

    }

    @Test
    public void shouldAllowSettingMyOwnCertificate() throws Exception {

        File certFile = new File(Configurator.DEFAULT_WEBSERVER_HTTPS_CERT_PATH);
        File keyFile = new File(
                Configurator.DEFAULT_WEBSERVER_HTTPS_KEY_PATH);

        String hostname = "my.very.own.hostname.org";

        createKeyAndCert(certFile, keyFile, hostname);

        NeoServerWithEmbeddedWebServer server = ServerBuilder.server().build();
        server.start();

        try {

            URI uri = new URI("https://localhost:"
                    + Configurator.DEFAULT_WEBSERVER_HTTPS_PORT + "/");

            ClientRequest req = ClientRequest.create()
                    .accept(MediaType.APPLICATION_JSON).build(uri, "GET");

            Client client = getClientThatOnlyAcceptsSslCertificatesWithHost(hostname);
            ClientResponse response = client.handle(req);

            assertThat(response.getStatus(), is(200));
        } finally {
            server.stop();
        }

    }

    @Test
    public void shouldAllowTurningOffHttpConnector() throws Exception {

        NeoServerWithEmbeddedWebServer server = ServerBuilder.server()
                .withHttpConnectorDisabled().build();
        server.start();

        try {

            URI uri = new URI("http://localhost:"
                    + Configurator.DEFAULT_WEBSERVER_PORT + "/");

            ClientRequest req = ClientRequest.create()
                    .accept(MediaType.APPLICATION_JSON).build(uri, "GET");

            Client client = getClientThatAcceptsAllSslCertificates();
            try {
                client.handle(req);
            } catch (ClientHandlerException e) {
                assertThat(e.getCause().getMessage(), is("Connection refused"));
                return;
            }
            
            fail("Expected server to not allow normal http connections.");
        } finally {
            server.stop();
        }

    }

    private void createKeyAndCert(File certPath, File keyFile, String hostName)
            throws Exception {
        FileOutputStream fos = null;
        try {
            Security.addProvider(new BouncyCastleProvider());

            KeyPairGenerator keyPairGenerator = KeyPairGenerator
                    .getInstance("RSA");
            keyPairGenerator.initialize(1024);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            X509V3CertificateGenerator certGenertor = new X509V3CertificateGenerator();

            certGenertor.setSerialNumber(BigInteger.valueOf(
                    new SecureRandom().nextInt()).abs());
            certGenertor.setIssuerDN(new X509Principal("CN=" + hostName
                    + ", OU=None, O=None L=None, C=None"));
            certGenertor.setNotBefore(new Date(System.currentTimeMillis()
                    - 1000L * 60 * 60 * 24 * 30));
            certGenertor.setNotAfter(new Date(System.currentTimeMillis()
                    + (1000L * 60 * 60 * 24 * 365 * 10)));
            certGenertor.setSubjectDN(new X509Principal("CN=" + hostName
                    + ", OU=None, O=None L=None, C=None"));

            certGenertor.setPublicKey(keyPair.getPublic());
            certGenertor.setSignatureAlgorithm("MD5WithRSAEncryption");

            Certificate certificate = certGenertor.generate(
                    keyPair.getPrivate(), "BC");

            fos = new FileOutputStream(certPath);
            fos.write(certificate.getEncoded());
            fos.close();

            fos = new FileOutputStream(keyFile);
            fos.write(keyPair.getPrivate().getEncoded());
            fos.close();

        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private Client getClientThatAcceptsAllSslCertificates() throws Exception {

        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs,
                    String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs,
                    String authType) {
            }

        } };

        HostnameVerifier hostNameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String arg0, SSLSession arg1) {
                return true;
            }
        };

        SSLContext ctx = SSLContext.getInstance("SSL");
        ctx.init(null, trustAllCerts, new java.security.SecureRandom());

        ClientConfig config = new DefaultClientConfig();
        config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES,
                new HTTPSProperties(hostNameVerifier, ctx));

        return Client.create(config);
    }

    private Client getClientThatOnlyAcceptsSslCertificatesWithHost(
            final String hostname) throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {

            }

            public void checkServerTrusted(X509Certificate[] certs,
                    String authType) {
                for (X509Certificate cert : certs) {
                    if (cert.getSubjectDN().getName().endsWith(hostname)) {
                        return;
                    }
                }
                throw new RuntimeException(
                        "Invalid ssl certificate. Expected one with hostname="
                                + hostname);
            }
        } };

        HostnameVerifier hostNameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String arg0, SSLSession arg1) {
                return true;
            }
        };

        SSLContext ctx = SSLContext.getInstance("SSL");
        ctx.init(null, trustAllCerts, new java.security.SecureRandom());

        ClientConfig config = new DefaultClientConfig();
        config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES,
                new HTTPSProperties(hostNameVerifier, ctx));

        return Client.create(config);
    }

}