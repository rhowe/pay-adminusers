package uk.gov.pay.adminusers.resources;

import com.google.common.collect.ImmutableMap;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static com.google.common.io.BaseEncoding.base32;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.hamcrest.core.Is.is;

public class UserResourceSecondFactorAuthenticationTest extends UserResourceTestBase {

    public static final String USER_2FA_AUTHENTICATE_URL = USER_2FA_URL + "/authenticate";

    @Test
    public void shouldCreate2FA_onForAValid2FAAuthRequest() throws Exception {
        String random = randomUUID().toString();
        createAValidUser(random);
        String username = "user-" + random;
        givenSetup()
                .when()
                .accept(JSON)
                .post(format(USER_2FA_URL, username))
                .then()
                .statusCode(200);
    }

    @Test
    public void shouldAuthenticate2FA_onForAValid2FAAuthRequest() throws Exception {
        String random = randomUUID().toString();
        createAValidUser(random);
        String username = "user-" + random;
        String otpSecret = "34f34";

        GoogleAuthenticator testAuthenticator = new GoogleAuthenticator(new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                .setCodeDigits(6)
                .setTimeStepSizeInMillis(TimeUnit.SECONDS.toMillis(60))
                .build());
        int passcode = testAuthenticator.getTotpPassword(base32().encode(otpSecret.getBytes()));
        ImmutableMap<String, Integer> authBody = ImmutableMap.of("code", passcode);

        givenSetup()
                .when()
                .accept(JSON)
                .body(mapper.writeValueAsString(authBody))
                .post(format(USER_2FA_AUTHENTICATE_URL, username))
                .then()
                .statusCode(200)
                .body("username", is("user-" + random))
                .body("login_counter", is(0))
                .body("disabled", is(false));
    }

    @Test
    public void shouldReturnNotFound_forNonExistentUser_when2FAAuthCreateRequest() throws Exception {
        String username = "non-existent";
        givenSetup()
                .when()
                .accept(JSON)
                .post(format(USER_2FA_URL, username))
                .then()
                .statusCode(404);
    }

    @Test
    public void shouldReturnUnauthorized_onInvalid2FACode_during2FAAuth() throws Exception {
        String random = randomUUID().toString();
        createAValidUser(random);

        String username = "user-" + random;
        int invalidPasscode = 111111;
        ImmutableMap<String, Integer> authBody = ImmutableMap.of("code", invalidPasscode);

        givenSetup()
                .when()
                .accept(JSON)
                .body(mapper.writeValueAsString(authBody))
                .post(format(USER_2FA_AUTHENTICATE_URL, username))
                .then()
                .statusCode(401);

    }

    @Test
    public void shouldReturnUnauthorizedAndAccountLocked_during2FAAuth_ifMaxRetryExceeded() throws Exception {
        String random = randomUUID().toString();
        createAValidUser(random);

        String username = "user-" + random;
        databaseTestHelper.updateLoginCount(username, 10);

        int invalidPasscode = 111111;
        ImmutableMap<String, Integer> authBody = ImmutableMap.of("code", invalidPasscode);

        givenSetup()
                .when()
                .accept(JSON)
                .body(mapper.writeValueAsString(authBody))
                .post(format(USER_2FA_AUTHENTICATE_URL, username))
                .then()
                .statusCode(401);

        givenSetup()
                .when()
                .contentType(JSON)
                .accept(JSON)
                .get(format(USER_RESOURCE_URL, username))
                .then()
                .statusCode(200)
                .body("username", is(username))
                .body("login_counter", is(11))
                .body("disabled", is(true));
    }

}