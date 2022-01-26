package cn.net.scp.nio.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

/**
 * A tool class that simplifies some SSL related tasks
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public final class SSLTools {

    // enforce noninstantiability
    private SSLTools() {
    }

    /**
     * creates a new SSLContext for an SSL client
     * @param trustStoreURL the URL to the truststore
     * @param trustStorePassword the password of the truststore
     * @return a new SSLContext for an SSL client
     * @throws IOException if an I/O exception occurs
     * @throws GeneralSecurityException if a security exception occurs (SSL)
     */
    public static SSLContext getClientSSLContext(
        URL trustStoreURL, String trustStorePassword)
        throws IOException, GeneralSecurityException {

        char[] password = trustStorePassword.toCharArray();
        KeyStore trustStore = loadKeyStore(trustStoreURL, password);
        KeyManagerFactory keyManagerFactory =
            createKeyManagerFactory(trustStore, password);
        TrustManagerFactory trustManagerFactory =
            createTrustManagerFactory(trustStore);
        return createSSLContext(keyManagerFactory, trustManagerFactory);
    }

    /**
     * creates a new SSLContext for an SSL server
     * @param trustStoreURL the URL to the truststore
     * @param trustStorePassword the password of the truststore
     * @param keyStoreURL the URL to the keystore
     * @param keyStorePassword the password of the keystore
     * @param alias the server certificate alias
     * @throws IOException if an I/O exception occurs
     * @throws GeneralSecurityException if a security exception occurs (SSL)
     * @return a new SSLContext for an SSL server
     */
    public static SSLContext getServerSSLContext(
        URL trustStoreURL, String trustStorePassword,
        URL keyStoreURL, String keyStorePassword,
        String alias) throws IOException, GeneralSecurityException {

        // create a TrustManagerFactory
        char[] tsPasswd = trustStorePassword.toCharArray();
        KeyStore trustStore = loadKeyStore(trustStoreURL, tsPasswd);
        TrustManagerFactory trustManagerFactory =
            createTrustManagerFactory(trustStore);

        // create KeyManagerFactory
        char[] ksPasswd = keyStorePassword.toCharArray();
        KeyStore keyStore = loadKeyStore(keyStoreURL, ksPasswd);
        KeyManagerFactory keyManagerFactory =
            createKeyManagerFactory(keyStore, ksPasswd);

        // check server certificate
        X509TrustManager x509TrustManager =
            getX509TrustManager(trustManagerFactory);
        checkCertificate(x509TrustManager, keyManagerFactory, alias);

        return createSSLContext(keyManagerFactory, trustManagerFactory);
    }

    /**
     * checks, if the certificate is valid with the given keyManagerFactory
     * @param x509TrustManager the X509TrustManager to use for certificate
     * verification
     * @param keyManagerFactory the KeyManagerFactory for getting the
     * certificate chain of the alias
     * @param alias the alias of the certificate to check
     * @throws GeneralSecurityException if a security exception occurs (SSL)
     */
    public static void checkCertificate(X509TrustManager x509TrustManager,
        KeyManagerFactory keyManagerFactory, String alias)
        throws GeneralSecurityException {

        // get the certificate chain for the provided alias
        X509KeyManager x509KeyManager = null;
        KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();
        for (KeyManager keyManager : keyManagers) {
            if (keyManager instanceof X509KeyManager) {
                x509KeyManager = (X509KeyManager) keyManager;
            }
        }
        if (x509KeyManager == null) {
            throw new GeneralSecurityException("no x509KeyManager found");
        }
        X509Certificate[] serverCertificateChain =
            x509KeyManager.getCertificateChain(alias);
        if (serverCertificateChain == null) {
            throw new GeneralSecurityException(
                "no certificate chain found for alias \"" + alias + "\"");
        }

        // this throws an exception if the certificateChain is not trusted
        x509TrustManager.checkServerTrusted(serverCertificateChain, "RSA");
    }

    /**
     * returns the X509TrustManager of a given TrustManagerFactory
     * @param trustManagerFactory the given TrustManagerFactory
     * @return the X509TrustManager of <CODE>trustManagerFactory</CODE>
     */
    public static X509TrustManager getX509TrustManager(
        TrustManagerFactory trustManagerFactory) {
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        for (TrustManager trustManager : trustManagers) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager) trustManager;
            }
        }
        return null;
    }

    /**
     * loads a JKS KeyStore from a given URL with a given password
     * @param keyStoreURL the URL where to load the KeyStore from
     * @param password the password of the keystore
     * @return the initialized KeyStore
     * @throws IOException if an I/O exception occurs
     * @throws GeneralSecurityException if a security exception occurs (SSL)
     */
    public static KeyStore loadKeyStore(URL keyStoreURL, char[] password)
        throws IOException, GeneralSecurityException {
        InputStream inputStream = keyStoreURL.openStream();
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(inputStream, password);
        return keyStore;
    }

    /**
     * creates a SunX509 KeyManagerFactory with a given KeyStore
     * @param keyStore the KeyStore to initialize the KeyManagerFactory
     * @param password the keystore password
     * @return a SunX509 KeyManagerFactory
     * @throws GeneralSecurityException if a security exception occurs (SSL)
     */
    public static KeyManagerFactory createKeyManagerFactory(
        KeyStore keyStore, char[] password)
        throws GeneralSecurityException {
        KeyManagerFactory keyManagerFactory =
            KeyManagerFactory.getInstance("SunX509");
        keyManagerFactory.init(keyStore, password);
        return keyManagerFactory;
    }

    /**
     * creates a SunX509 TrustManagerFactory with a given KeyStore
     * @param trustStore the KeyStore to initialize the TrustManagerFactory
     * @return a SunX509 TrustManagerFactory
     * @throws GeneralSecurityException if a security exception occurs (SSL)
     */
    public static TrustManagerFactory createTrustManagerFactory(
        KeyStore trustStore) throws GeneralSecurityException {
        TrustManagerFactory trustManagerFactory =
            TrustManagerFactory.getInstance("SunX509");
        trustManagerFactory.init(trustStore);
        return trustManagerFactory;
    }

    private static SSLContext createSSLContext(
        KeyManagerFactory keyManagerFactory,
        TrustManagerFactory trustManagerFactory)
        throws GeneralSecurityException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
            keyManagerFactory.getKeyManagers(),
            trustManagerFactory.getTrustManagers(),
            null);
        return sslContext;
    }
}
