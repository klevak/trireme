package com.apigee.noderunner.crypto;

import com.apigee.noderunner.core.internal.CryptoException;
import com.apigee.noderunner.core.internal.CryptoService;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class CryptoServiceImpl
    implements CryptoService
{
    private static final Logger log = LoggerFactory.getLogger(CryptoServiceImpl.class);

    public static final Charset ASCII = Charset.forName("ASCII");
    public static final String RSA = "RSA";
    public static final String DSA = "DSA";

    @Override
    public KeyPair readKeyPair(String algorithm, InputStream is, char[] passphrase)
        throws IOException, CryptoException
    {
        PEMParser pp = new PEMParser(new InputStreamReader(is, ASCII));
        try {
            Object po = pp.readObject();
            if (log.isDebugEnabled()) {
                log.debug("Trying to read an {} key pair and got {}", algorithm, po);
            }
            if (po instanceof PEMKeyPair) {
                return convertKeyPair(algorithm, (PEMKeyPair)po);
            }
            if (po instanceof PEMEncryptedKeyPair) {
                PEMDecryptorProvider dec =
                    new JcePEMDecryptorProviderBuilder().build(passphrase);
                PEMKeyPair kp = ((PEMEncryptedKeyPair)po).decryptKeyPair(dec);
                return convertKeyPair(algorithm, kp);
            }
            throw new CryptoException("Input data does not contain a key pair");
        } finally {
            pp.close();
        }
    }

    private KeyPair convertKeyPair(String algorithm, PEMKeyPair kp)
        throws IOException, CryptoException
    {
        if (RSA.equals(algorithm)) {
            return RSAConverter.convertKeyPair(kp);
        } else if (DSA.equals(algorithm)) {
            return DSAConverter.convertKeyPair(kp);
        } else {
            throw new CryptoException("Unknown algorithm " + algorithm);
        }
    }

    @Override
    public PublicKey readPublicKey(String algorithm, InputStream is)
        throws IOException, CryptoException
    {
        PEMParser pp = new PEMParser(new InputStreamReader(is, ASCII));
        try {
            Object po = pp.readObject();
            if (log.isDebugEnabled()) {
                log.debug("Trying to read an {} public key and got {}", algorithm, po);
            }

            if (po instanceof SubjectPublicKeyInfo) {
                return convertPublicKey(algorithm, (SubjectPublicKeyInfo) po);
            }
            throw new CryptoException("Input data does not contain a public key");
        } finally {
            pp.close();
        }
    }

    private PublicKey convertPublicKey(String algorithm, SubjectPublicKeyInfo pk)
        throws IOException, CryptoException
    {
        if ("RSA".equals(algorithm)) {
            return RSAConverter.convertPublicKey(pk);
        }
        throw new CryptoException("Unknown algorithm " + algorithm);
    }

    @Override
    public X509Certificate readCertificate(InputStream is)
        throws IOException, CryptoException
    {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate)cf.generateCertificate(is);
        } catch (GeneralSecurityException gse) {
            throw new CryptoException(gse);
        }
    }

    @Override
    public KeyStore createPemKeyStore()
    {
        ProviderLoader.get().ensureLoaded();
        try {
            return KeyStore.getInstance(NoderunnerProvider.ALGORITHM, NoderunnerProvider.NAME);
        } catch (KeyStoreException e) {
            throw new AssertionError(e);
        } catch (NoSuchProviderException e) {
            throw new AssertionError(e);
        }
    }
}
