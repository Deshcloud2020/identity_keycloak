package org.keycloak.testsuite.authz;

import org.junit.BeforeClass;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.representations.AccessToken;
import org.keycloak.testsuite.AbstractKeycloakTest;
import org.keycloak.testsuite.ProfileAssume;

import static org.keycloak.common.Profile.Feature.AUTHORIZATION;

/**
 * @author mhajas
 */
public abstract class AbstractAuthzTest extends AbstractKeycloakTest {

    @BeforeClass
    public static void enabled() {
        ProfileAssume.assumeFeatureEnabled(AUTHORIZATION);
    }

    protected AccessToken toAccessToken(String rpt) {
        AccessToken accessToken;

        try {
            accessToken = new JWSInput(rpt).readJsonContent(AccessToken.class);
        } catch (JWSInputException cause) {
            throw new RuntimeException("Failed to deserialize RPT", cause);
        }
        return accessToken;
    }
}
