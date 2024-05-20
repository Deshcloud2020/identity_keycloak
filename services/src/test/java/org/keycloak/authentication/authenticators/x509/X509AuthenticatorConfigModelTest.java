package org.keycloak.authentication.authenticators.x509;

import org.junit.Assert;
import org.junit.Test;

/**
 * author Pascal Knueppel <br>
 * created at: 02.12.2019 - 10:59 <br>
 * <br>
 *
 */
public class X509AuthenticatorConfigModelTest {

    /**
     * this test will verify that no exception occurs if no settings are stored for the timestamp validation
     */
    @Test
    public void testTimestampValidationAttributeReturnsNull() {
        X509AuthenticatorConfigModel configModel = new X509AuthenticatorConfigModel();
        Assert.assertNull(configModel.getConfig().get(AbstractX509ClientCertificateAuthenticator.TIMESTAMP_VALIDATION));
        Assert.assertFalse(configModel.isCertValidationEnabled());
    }

    /**
     * this test will verify that no exception occurs if no settings are stored for the certificate policy validation
     */
    @Test
    public void testCertificatePolicyValidationAttributeReturnsNull() {
        X509AuthenticatorConfigModel configModel = new X509AuthenticatorConfigModel();
        Assert.assertNull(configModel.getConfig().get(AbstractX509ClientCertificateAuthenticator.CERTIFICATE_POLICY));
        Assert.assertNull(configModel.getCertificatePolicy());
    }

    /**
     * this test will verify that no exception occurs and ALL will be returned if no settings are stored for the certificate policy mode setting
     */
    @Test
    public void testCertificatePolicyModeValidationAttributeReturnsAll() {
        X509AuthenticatorConfigModel configModel = new X509AuthenticatorConfigModel();
        Assert.assertNull(configModel.getConfig().get(AbstractX509ClientCertificateAuthenticator.CERTIFICATE_POLICY_MODE));
        Assert.assertEquals(AbstractX509ClientCertificateAuthenticator.CERTIFICATE_POLICY_MODE_ALL, configModel.getCertificatePolicyMode().getMode());
    }
}
