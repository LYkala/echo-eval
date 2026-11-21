/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import android.annotation.SuppressLint;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.CaCertType;
import com.huawei.aisfsdkv2.api.base.DeviceCertType;
import com.huawei.aisfsdkv2.api.base.TLSConfig;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;

/**
 * SSL/TLS 工具类
 *
 * <p>根据TLSConfig为Netty客户端配置SSL策略，支持单向认证和双向认证（mTLS）。
 * CA证书类型由{@link CaCertType}决定，设备证书类型由{@link DeviceCertType}决定。</p>
 */
public final class SSLUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("SSLUtil");

    /**
     * 私有构造方法，防止实例化
     */
    private SSLUtil() {
}

    // ==================== TrustManager 相关 ====================

    /**
     * 根据TLSConfig解析TrustManager数组
     *
     * <p>策略说明：
     * <ul>
     *   <li>CaCertType.X509：从CA证书文件加载CaCertTrustManager，PKIX验证服务端证书链</li>
     *   <li>CaCertType.NONE：返回信任所有证书的TrustAllTrustManager，
     *       跳过服务端证书校验（信任所有服务端证书）。
     *       适用于不开启CA证书认证的场景（自签名证书、私有CA等）。</li>
     * </ul>
     * <p>兜底策略：CA证书路径为空或加载失败时，降级为TrustAllTrustManager（信任所有）。</p>
     *
     * @param tlsConfig TLS配置
     * @return TrustManager数组，CaCertType.NONE或CA加载失败时返回 TrustAllTrustManager
     */
    private static TrustManager[] resolveTrustManagers(TLSConfig tlsConfig) {
        CaCertType caCertType = tlsConfig.getCaCertType();
        if (caCertType == CaCertType.X509) {
            String caFilePath = tlsConfig.getCaFilePath();
            if (caFilePath == null || caFilePath.isEmpty()) {
                LOGGER.warn("SSLUtil.resolveTrustManagers: CaCertType is {} but caFilePath is empty, "
                        + "fallback to TrustAll", caCertType);
                return new TrustManager[]{createTrustAllTrustManager()};
            }
            TrustManager[] trustManagers = createTrustManagersFromCaCert(caFilePath);
            if (trustManagers == null) {
                LOGGER.error("SSLUtil.resolveTrustManagers: Failed to create TrustManager from CA cert, "
                        + "fallback to TrustAll");
                return new TrustManager[]{createTrustAllTrustManager()};
            }
            return trustManagers;
        }
        LOGGER.info("SSLUtil.resolveTrustManagers: CaCertType is {}, using TrustAll TrustManager", caCertType);
        return new TrustManager[]{createTrustAllTrustManager()};
    }

    /**
     * 从CA证书文件创建TrustManager数组
     *
     * <p>直接使用CertPathValidator验证证书链，绕过Android TrustManagerFactory
     * 对自定义KeyStore不生效的问题（Android上TrustManagerFactory.init(keyStore)
     * 可能返回RootTrustManager而忽略自定义CA证书）。</p>
     *
     * @param caFilePath CA证书文件路径
     * @return TrustManager数组，加载失败返回 null
     */
    private static TrustManager[] createTrustManagersFromCaCert(String caFilePath) {
        try (InputStream is = Files.newInputStream(Paths.get(caFilePath))) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate caCert = (X509Certificate) cf.generateCertificate(is);
            return new TrustManager[]{new CaCertTrustManager(caCert)};
        } catch (IOException | CertificateException e) {
            LOGGER.error("SSLUtil.createTrustManagersFromCaCert: Failed to load CA cert: {}", caFilePath, e);
            return null;
        }
    }

    /**
     * 基于CA证书的TrustManager
     *
     * <p>使用CertPathValidator + PKIXParameters直接以CA证书为TrustAnchor验证服务端证书链，
     * 绕过Android TrustManagerFactory对自定义KeyStore不生效的问题。</p>
     */
    @SuppressLint("CustomX509TrustManager")
    private static class CaCertTrustManager implements X509TrustManager {
        private final X509Certificate caCert;

        CaCertTrustManager(X509Certificate caCert) {
            this.caCert = caCert;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (chain == null || chain.length == 0) {
                throw new CertificateException("Certificate chain is empty");
            }
            try {
                CertPathValidator validator = CertPathValidator.getInstance("PKIX");
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                CertPath certPath = cf.generateCertPath(Arrays.asList(chain));
                TrustAnchor anchor = new TrustAnchor(caCert, null);
                PKIXParameters params = new PKIXParameters(Collections.singleton(anchor));
                params.setRevocationEnabled(false);
                validator.validate(certPath, params);
            } catch (CertPathValidatorException e) {
                throw new CertificateException("Certificate chain validation failed", e);
            } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
                throw new CertificateException("Certificate validation error", e);
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{caCert};
        }
    }

    /**
     * 创建信任所有证书的 X509TrustManager（跳过CA校验和hostname校验）
     *
     * <p><strong>警告：此 TrustManager 会接受任何 SSL 证书，不验证证书有效性。
     * 仅用于不开启证书认证（CaCertType.NONE）或证书加载失败的兜底策略。</strong></p>
     *
     * @return X509TrustManager 实例
     */
    @SuppressLint("CustomX509TrustManager")
    private static X509TrustManager createTrustAllTrustManager() {
        return new X509TrustManager() {
            @SuppressLint("TrustAllX509TrustManager")
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @SuppressLint("TrustAllX509TrustManager")
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[]{};
            }
        };
    }

    // ==================== KeyManager 相关 ====================

    /**
     * 根据TLSConfig解析KeyManager数组
     *
     * <p>DeviceCertType.PEM或PKCS12时从设备证书加载，其他情况返回null（无客户端认证）。</p>
     *
     * @param tlsConfig TLS配置
     * @return KeyManager数组，无设备证书时返回 null
     */
    private static KeyManager[] resolveKeyManagers(TLSConfig tlsConfig) {
        DeviceCertType deviceCertType = tlsConfig.getDeviceCertType();
        if (deviceCertType == DeviceCertType.PEM) {
            return createKeyManagersFromPEM(tlsConfig);
        } else if (deviceCertType == DeviceCertType.PKCS12) {
            return createKeyManagersFromPKCS12(tlsConfig);
        }
        return null;
    }

    /**
     * 从PEM格式的设备证书创建KeyManager数组
     *
     * <p>证书和私钥均支持从内容（deviceCertContent/deviceKeyContent）或文件路径（deviceCertPath）加载，
     * 内容优先于文件路径。</p>
     *
     * @param tlsConfig TLS配置，包含deviceCertContent/deviceCertPath、deviceKeyContent、deviceCertPassword
     * @return KeyManager数组，加载失败返回 null
     */
    private static KeyManager[] createKeyManagersFromPEM(TLSConfig tlsConfig) {
        char[] passwordChars = tlsConfig.getDeviceCertPassword();

        try {
            // 加载证书：优先从deviceCertContent加载，否则从deviceCertPath文件加载
            Certificate cert;
            byte[] certContent = tlsConfig.getDeviceCertContent();
            if (certContent != null && certContent.length > 0) {
                LOGGER.info("SSLUtil.createKeyManagersFromPEM: Loading PEM device cert from content (bytes)");
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                cert = cf.generateCertificate(new ByteArrayInputStream(certContent));
            } else {
                String certPath = tlsConfig.getDeviceCertPath();
                if (certPath == null || certPath.isEmpty()) {
                    LOGGER.error("SSLUtil.createKeyManagersFromPEM: deviceCertContent is empty and "
                            + "deviceCertPath is empty, cannot load cert");
                    return null;
                }
                LOGGER.info("SSLUtil.createKeyManagersFromPEM: Loading PEM device cert, certPath={}", certPath);
                try (InputStream is = Files.newInputStream(Paths.get(certPath))) {
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    cert = cf.generateCertificate(is);
                }
            }

            byte[] keyContent = tlsConfig.getDeviceKeyContent();
            if (keyContent == null || keyContent.length == 0) {
                LOGGER.error("SSLUtil.createKeyManagersFromPEM: deviceKeyContent is empty");
                return null;
            }
            LOGGER.info("SSLUtil.createKeyManagersFromPEM: Loading private key from content (bytes)");
            PrivateKey privateKey = loadPrivateKeyFromBytes(keyContent, passwordChars);
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setKeyEntry("device", privateKey,
                    passwordChars != null ? passwordChars : new char[0], new Certificate[]{cert});
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, passwordChars != null ? passwordChars : new char[0]);
            LOGGER.info("SSLUtil.createKeyManagersFromPEM: KeyManager created from PEM, cert loaded from {}",
                    (certContent != null && certContent.length > 0) ? "content" : "file");
            return kmf.getKeyManagers();
        } catch (Exception e) {
            LOGGER.error("SSLUtil.createKeyManagersFromPEM: Failed to load PEM device cert", e);
            return null;
        }
    }

    /**
     * 从PKCS12格式的设备证书库创建KeyManager数组
     *
     * <p>直接使用TLSConfig中的静态配置字段获取密码。</p>
     *
     * @param tlsConfig TLS配置，包含deviceCertPath和deviceCertPassword
     * @return KeyManager数组，加载失败返回 null
     */
    private static KeyManager[] createKeyManagersFromPKCS12(TLSConfig tlsConfig) {
        String p12Path = tlsConfig.getDeviceCertPath();
        if (p12Path == null || p12Path.isEmpty()) {
            LOGGER.error("SSLUtil.createKeyManagersFromPKCS12: deviceCertPath is empty");
            return null;
        }

        char[] passwordChars = tlsConfig.getDeviceCertPassword();

        try {
            LOGGER.info("SSLUtil.createKeyManagersFromPKCS12: Loading PKCS12 device cert, certPath={}", p12Path);
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream is = Files.newInputStream(Paths.get(p12Path))) {
                keyStore.load(is, passwordChars != null ? passwordChars : new char[0]);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, passwordChars != null ? passwordChars : new char[0]);
            LOGGER.info("SSLUtil.createKeyManagersFromPKCS12: KeyManager created from PKCS12, certPath={}", p12Path);
            return kmf.getKeyManagers();
        } catch (Exception e) {
            LOGGER.error("SSLUtil.createKeyManagersFromPKCS12: Failed to load PKCS12 device cert", e);
            return null;
        }
    }

    /**
     * 从PEM字节数组加载私钥
     *
     * <p>使用BouncyCastle的PEMParser解析PEM格式私钥内容，支持以下格式：
     * <ul>
     *   <li>未加密的PKCS8私钥（BEGIN PRIVATE KEY）</li>
     *   <li>未加密的PKCS1私钥（BEGIN RSA PRIVATE KEY / BEGIN EC PRIVATE KEY）</li>
     *   <li>加密的PKCS1私钥（BEGIN RSA PRIVATE KEY + DEK-Info，需提供口令）</li>
     * </ul>
     *
     * <p>此方法供App侧直接传入私钥内容（从assets、安全存储、网络下载等方式获取）</p>
     *
     * @param keyContent 私钥内容（PEM格式字节数组）
     * @param password 私钥口令，加密私钥时必须提供；未加密私钥时可传null
     * @return PrivateKey 实例
     * @throws Exception 密钥解析失败
     */
    private static PrivateKey loadPrivateKeyFromBytes(byte[] keyContent, char[] password) throws Exception {
        try (PEMParser pemParser = new PEMParser(new InputStreamReader(
                new ByteArrayInputStream(keyContent), java.nio.charset.StandardCharsets.UTF_8))) {
            Object object = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (object instanceof PEMEncryptedKeyPair) {
                if (password == null || password.length == 0) {
                    throw new IllegalArgumentException("Encrypted private key requires password");
                }
                PEMDecryptorProvider decryptorProvider = new JcePEMDecryptorProviderBuilder()
                        .setProvider(new BouncyCastleProvider()).build(password);
                PEMKeyPair keyPair = ((PEMEncryptedKeyPair) object).decryptKeyPair(decryptorProvider);
                return converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            } else if (object instanceof PEMKeyPair) {
                return converter.getPrivateKey(((PEMKeyPair) object).getPrivateKeyInfo());
            } else if (object instanceof PrivateKeyInfo) {
                return converter.getPrivateKey((PrivateKeyInfo) object);
            } else {
                throw new IllegalArgumentException("Unsupported PEM object type: " + object.getClass().getName());
            }
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 从TrustManager数组中查找X509TrustManager
     *
     * @param trustManagers TrustManager数组
     * @return X509TrustManager实例，未找到返回 null
     */
    private static X509TrustManager findX509TrustManager(TrustManager[] trustManagers) {
        for (TrustManager tm : trustManagers) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        return null;
    }

    // ==================== Netty SSL 支持 ====================

    /**
     * 为 Netty HTTP 客户端创建 SslContext
     *
     * <p>根据 TLSConfig 创建适用于 Netty 的 SslContext，支持：
     * <ul>
     *   <li>信任所有模式：CaCertType.NONE + DeviceCertType.NONE，
     *       信任所有服务端自定义证书（含自签名、私有CA等），不发送客户端证书</li>
     *   <li>单向认证：CaCertType.X509 + DeviceCertType.NONE，
     *       自定义CA证书验证服务端证书链，不发送客户端证书</li>
     *   <li>双向认证：CaCertType.X509 + DeviceCertType.PEM/PKCS12，
     *       自定义CA证书验证服务端 + 发送客户端证书</li>
     *   <li>客户端证书模式：CaCertType.NONE + DeviceCertType.PEM/PKCS12，
     *       信任所有服务端证书 + 发送客户端证书</li>
     * </ul>
     *
     * <p>兜底策略：</p>
     * <ul>
     *   <li>TLSConfig为null → TrustAll SslContext</li>
     *   <li>CA证书路径为空或加载失败 → TrustAllTrustManager（信任所有服务端证书）</li>
     *   <li>SslContext构建异常 → TrustAll SslContext</li>
     *   <li>TrustAll构建也失败 → 返回null（由调用方兜底为JDK系统默认）</li>
     * </ul>
     *
     * <p>注意：Netty 的 SslContextBuilder API 与 OkHttp 不同：
     * <ul>
     *   <li>trustManager() 支持传入 X509TrustManager</li>
     *   <li>不调用trustManager()时，使用JDK默认TrustManager（信任系统内置公共CA证书）</li>
     *   <li>keyManager() 支持传入 KeyManager[] 或证书文件路径</li>
     * </ul>
     *
     * @return SslContext 实例，创建失败返回 null
     */
    public static SslContext createSslContextForNetty() {
        TLSConfig tlsConfig = AISFSDKClient.getBaseApi().getTLSConfig();
        if (tlsConfig == null) {
            LOGGER.info("SSLUtil.createSslContextForNetty: TLSConfig is null, using TrustAll");
            return createTrustAllSslContext();
        }

        try {
            TrustManager[] trustManagers = resolveTrustManagers(tlsConfig);
            KeyManager[] keyManagers = resolveKeyManagers(tlsConfig);
            SslContextBuilder builder = SslContextBuilder.forClient();

            // 设置 TrustManager
            // CaCertType.NONE时，trustManagers=TrustAllTrustManager（信任所有服务端自定义证书）
            // CaCertType.X509时，trustManagers=CaCertTrustManager（自定义CA证书PKIX验证）
            // trustManagers始终不为null（NONE模式返回TrustAll，X509模式加载失败降级为TrustAll）
            if (trustManagers != null) {
                X509TrustManager trustManager = findX509TrustManager(trustManagers);
                if (trustManager != null) {
                    builder.trustManager(trustManager);
                }
            }

            if (keyManagers != null && keyManagers.length > 0) {
                builder.keyManager(keyManagers[0]);
            }
            SslContext sslContext = builder.build();
            LOGGER.info("SSLUtil.createSslContextForNetty: SslContext created, caCertType={}, deviceCertType={}",
                    tlsConfig.getCaCertType(), tlsConfig.getDeviceCertType());
            return sslContext;

        } catch (SSLException e) {
            LOGGER.error("SSLUtil.createSslContextForNetty: Failed to create SslContext", e);
            return createTrustAllSslContext();
        }
    }

    /**
     * 创建信任所有证书的 SslContext（TrustAll 模式）
     *
     * <p><strong>警告：此 SslContext 会接受所有 SSL 证书，不验证证书有效性。
     * 仅用于TLSConfig为null或证书构建失败时的兜底策略。</strong></p>
     *
     * @return SslContext 实例，创建失败返回 null
     */
    private static SslContext createTrustAllSslContext() {
        try {
            return SslContextBuilder.forClient()
                    .trustManager(createTrustAllTrustManager())
                    .build();
        } catch (SSLException e) {
            LOGGER.error("SSLUtil.createTrustAllSslContext: Failed to create TrustAll SslContext", e);
            return null;
        }
    }
}
