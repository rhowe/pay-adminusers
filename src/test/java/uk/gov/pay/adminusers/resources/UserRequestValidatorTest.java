package uk.gov.pay.adminusers.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.adminusers.utils.Errors;
import uk.gov.pay.adminusers.validations.RequestValidations;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UserRequestValidatorTest {

    private UserRequestValidator validator;

    private ObjectMapper objectMapper;

    @Before
    public void before() {
        validator = new UserRequestValidator(new RequestValidations());
        objectMapper = new ObjectMapper();
    }

    @Test
    public void shouldError_ifAllMandatoryFieldsAreMissing() throws Exception {
        String invalidPayload = "{}";
        JsonNode jsonNode = objectMapper.readTree(invalidPayload);
        Optional<Errors> optionalErrors = validator.validateCreateRequest(jsonNode);

        assertTrue(optionalErrors.isPresent());
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(4));
        assertThat(errors.getErrors(), hasItems(
                "Field [username] is required",
                "Field [email] is required",
                "Field [telephone_number] is required",
                "Field [role_name] is required"));
    }

    @Test
    public void shouldError_ifSomeMandatoryFieldsAreMissing() throws Exception {
        String invalidPayload = "{" +
                "\"username\": \"a-username\"," +
                "\"email\": \"email@example.com\"," +
                "\"otp_key\": \"12345\"" +
                "}";
        JsonNode jsonNode = objectMapper.readTree(invalidPayload);

        Optional<Errors> optionalErrors = validator.validateCreateRequest(jsonNode);

        assertTrue(optionalErrors.isPresent());
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(2));
        assertThat(errors.getErrors(), hasItems(
                "Field [role_name] is required",
                "Field [telephone_number] is required"));

    }

    @Test
    public void shouldError_ifMandatoryPatchFieldsAreMissing() {
        JsonNode invalidPayload = mock(JsonNode.class);
        mockValidValuesFor(invalidPayload, Map.of("foo", "blah", "bar", "blah@blah.com"));
        Optional<Errors> optionalErrors = validator.validatePatchRequest(invalidPayload);

        assertTrue(optionalErrors.isPresent());
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(3));
        assertThat(errors.getErrors(), hasItems(
                "Field [op] is required",
                "Field [path] is required",
                "Field [value] is required"));
    }

    @Test
    public void shouldError_ifPathNotAllowed_whenPatching() {
        JsonNode invalidPayload = mock(JsonNode.class);
        mockValidValuesFor(invalidPayload, Map.of("op", "append", "path", "version", "value", "1"));
        Optional<Errors> optionalErrors = validator.validatePatchRequest(invalidPayload);

        assertTrue(optionalErrors.isPresent());
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(1));
        assertThat(errors.getErrors(), hasItems("Patching path [version] not allowed"));
    }

    @Test
    public void shouldError_ifPathOperationNotValid_whenPatching() {
        JsonNode invalidPayload = mock(JsonNode.class);
        mockValidValuesFor(invalidPayload, Map.of("op", "replace", "path", "sessionVersion", "value", "1"));
        Optional<Errors> optionalErrors = validator.validatePatchRequest(invalidPayload);

        assertTrue(optionalErrors.isPresent());
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(1));
        assertThat(errors.getErrors(), hasItems("Operation [replace] not allowed for path [sessionVersion]"));
    }

    @Test
    public void shouldError_ifSessionVersionNotNumeric_whenPatching() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("op", "append", "path", "sessionVersion", "value", "1r"));
        Optional<Errors> optionalErrors = validator.validatePatchRequest(payload);

        assertTrue(optionalErrors.isPresent());
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(1));
        assertThat(errors.getErrors(), hasItems("path [sessionVersion] must contain a value of positive integer"));
    }

    @Test
    public void shouldError_ifDisabledNotBoolean_whenPatching() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("op", "replace", "path", "disabled", "value", "1r"));
        Optional<Errors> optionalErrors = validator.validatePatchRequest(payload);

        assertTrue(optionalErrors.isPresent());
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(1));
        assertThat(errors.getErrors(), hasItems("path [disabled] must be contain value [true | false]"));
    }

    @Test
    public void shouldSuccess_forDisabled_whenPatching() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("op", "replace", "path", "disabled", "value", "true"));
        Optional<Errors> optionalErrors = validator.validatePatchRequest(payload);

        assertFalse(optionalErrors.isPresent());
    }

    @Test
    public void shouldSuccess_forSessionVersion_whenPatching() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("op", "append", "path", "sessionVersion", "value", "2"));
        Optional<Errors> optionalErrors = validator.validatePatchRequest(payload);

        assertFalse(optionalErrors.isPresent());
    }

    @Test
    public void shouldSuccess_replacingTelephoneNumber_whenPatchingLocalTelephoneNumber() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("op", "replace", "path", "telephone_number", "value", "01134960000"));
        Optional<Errors> optionalErrors = validator.validatePatchRequest(payload);

        assertFalse(optionalErrors.isPresent());
    }

    @Test
    public void shouldSuccess_replacingTelephoneNumber_whenPatchingInternationalTelephoneNumber() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("op", "replace", "path", "telephone_number", "value", "+441134960000"));
        Optional<Errors> optionalErrors = validator.validatePatchRequest(payload);

        assertFalse(optionalErrors.isPresent());
    }

    @Test
    public void shouldError_replacingTelephoneNumber_whenPatchingInvalidTelephoneNumber() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("op", "replace", "path", "telephone_number", "value", "(╯°□°）╯︵ ┻━┻"));
        Optional<Errors> optionalErrors = validator.validatePatchRequest(payload);

        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(1));
        assertThat(errors.getErrors(), hasItems("path [telephone_number] must contain a valid telephone number"));
    }

    @Test
    public void shouldSuccess_whenAddingServiceRole() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("service_external_id", "blah-blah", "role_name", "blah"));
        Optional<Errors> optionalErrors = validator.validateAssignServiceRequest(payload);

        assertFalse(optionalErrors.isPresent());
    }

    @Test
    public void shouldError_whenAddingServiceRole_ifRequiredParamMissing() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("service_external_id", "blah-blah"));
        Optional<Errors> optionalErrors = validator.validateAssignServiceRequest(payload);

        Errors errors = optionalErrors.get();
        assertThat(errors.getErrors().size(), is(1));
        assertThat(errors.getErrors(), hasItems(
                "Field [role_name] is required"));
    }

    @Test
    public void shouldError_ifTelephoneNumberFieldIsInvalid() throws Exception {
        String invalidPayload = "{" +
                "\"username\": \"a-username\"," +
                "\"password\": \"a-password\"," +
                "\"email\": \"email@example.com\"," +
                "\"gateway_account_ids\": [\"1\"]," +
                "\"telephone_number\": \"(╯°□°）╯︵ ┻━┻\"," +
                "\"otp_key\": \"12345\"," +
                "\"role_name\": \"a-role\"" +
                "}";
        JsonNode jsonNode = objectMapper.readTree(invalidPayload);
        Optional<Errors> optionalErrors = validator.validateCreateRequest(jsonNode);

        assertTrue(optionalErrors.isPresent());
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(1));
        assertThat(errors.getErrors(), hasItems(
                "Field [telephone_number] must be a valid telephone number"));

    }

    @Test
    public void shouldError_ifFieldsAreBiggerThanMaxLength() throws Exception {
        String invalidPayload = "{" +
                "\"username\": \"" + RandomStringUtils.randomAlphanumeric(256) + "\"," +
                "\"password\": \"" + RandomStringUtils.randomAlphanumeric(256) + "\"," +
                "\"email\": \"email@example.com\"," +
                "\"gateway_account_ids\": [\"1\"]," +
                "\"telephone_number\": \"07990000000\"," +
                "\"otp_key\": \"12345\"," +
                "\"role_name\": \"a-role\"" +
                "}";
        JsonNode jsonNode = objectMapper.readTree(invalidPayload);
        Optional<Errors> optionalErrors = validator.validateCreateRequest(jsonNode);

        assertTrue(optionalErrors.isPresent());
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(1));
        assertThat(errors.getErrors(), hasItems(
                "Field [username] must have a maximum length of 255 characters"));

    }

    @Test
    public void shouldReturnEmpty_ifAllValidationsArePassed() throws Exception {
        String validPayload = "{" +
                "\"username\": \"a-username\"," +
                "\"password\": \"a-password\"," +
                "\"email\": \"email@example.com\"," +
                "\"gateway_account_ids\": [\"1\"]," +
                "\"telephone_number\": \"01134960000\"," +
                "\"otp_key\": \"12345\"," +
                "\"role_name\": \"a-role\"" +
                "}";
        JsonNode jsonNode = objectMapper.readTree(validPayload);
        Optional<Errors> optionalErrors = validator.validateCreateRequest(jsonNode);

        assertFalse(optionalErrors.isPresent());
    }

    @Test
    public void shouldSuccess_ifValidSearchRequest_whenFindingAUser() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("username", "some-existing-user"));
        Optional<Errors> optionalErrors = validator.validateFindRequest(payload);

        assertThat(optionalErrors.isPresent(), is(false));
    }

    @Test
    public void shouldSuccess_ifNoBody_whenValidateNewSecondFactorPasscodeRequest() {
        Optional<Errors> optionalErrors = validator.validateNewSecondFactorPasscodeRequest(null);

        assertThat(optionalErrors.isPresent(), is(false));
    }

    @Test
    public void shouldSuccess_ifProvisionalNotPresent_whenValidateNewSecondFactorPasscodeRequest() {
        JsonNode payload = new ObjectMapper().valueToTree(Collections.emptyMap());

        Optional<Errors> optionalErrors = validator.validateNewSecondFactorPasscodeRequest(payload);

        assertThat(optionalErrors.isPresent(), is(false));
    }

    @Test
    public void shouldSuccess_ifProvisionalTrue_whenValidateNewSecondFactorPasscodeRequest() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("provisional", true));

        Optional<Errors> optionalErrors = validator.validateNewSecondFactorPasscodeRequest(payload);

        assertThat(optionalErrors.isPresent(), is(false));
    }

    @Test
    public void shouldSuccess_ifProvisionalFalse_whenValidateNewSecondFactorPasscodeRequest() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("provisional", false));

        Optional<Errors> optionalErrors = validator.validateNewSecondFactorPasscodeRequest(payload);

        assertThat(optionalErrors.isPresent(), is(false));
    }

    @Test
    public void shouldError_ifProvisionalNotBoolean_whenValidateNewSecondFactorPasscodeRequest() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("provisional", "maybe"));

        Optional<Errors> optionalErrors = validator.validateNewSecondFactorPasscodeRequest(payload);

        assertTrue(optionalErrors.isPresent());
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(1));
        assertThat(errors.getErrors(), hasItems("Field [provisional] must be a boolean"));
    }

    @Test
    public void shouldError_ifCodeMissing_whenValidate2faActivateRequest() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("second_factor", "SMS"));

        Optional<Errors> optionalErrors = validator.validate2faActivateRequest(payload);

        assertTrue(optionalErrors.isPresent());
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(1));
        assertThat(errors.getErrors(), hasItems("Field [code] is required"));
    }

    @Test
    public void shouldError_ifCodeNotNumeric_whenValidate2faActivateRequest() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("code", "I am not a number, I’m a free man!",
                "second_factor", "SMS"));

        Optional<Errors> optionalErrors = validator.validate2faActivateRequest(payload);

        assertTrue(optionalErrors.isPresent());
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(1));
        assertThat(errors.getErrors(), hasItems("Field [code] must be a number"));
    }

    @Test
    public void shouldError_ifSecondFactorMissing_whenValidate2faActivateRequest() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("code", 123456));

        Optional<Errors> optionalErrors = validator.validate2faActivateRequest(payload);

        assertTrue(optionalErrors.isPresent());
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(1));
        assertThat(errors.getErrors(), hasItems("Field [second_factor] is required"));
    }

    @Test
    public void shouldError_ifSecondFactorInvalid_whenValidate2faActivateRequest() {
        JsonNode payload = new ObjectMapper().valueToTree(Map.of("code", 123456,
                "second_factor", "PINKY_SWEAR"));

        Optional<Errors> optionalErrors = validator.validate2faActivateRequest(payload);

        assertTrue(optionalErrors.isPresent());
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(1));
        assertThat(errors.getErrors(), hasItems("Invalid second_factor [PINKY_SWEAR]"));
    }

    @Test
    public void shouldError_ifRequiredFieldsMissing_whenFindingAUser() throws Exception {
        JsonNode payload = new ObjectMapper().readTree("{}");
        Optional<Errors> optionalErrors = validator.validateFindRequest(payload);

        assertTrue(optionalErrors.isPresent());
        Errors errors = optionalErrors.get();

        assertThat(errors.getErrors().size(), is(1));
        assertThat(errors.getErrors(), hasItems("Field [username] is required"));
    }

    private void mockValidValuesFor(JsonNode mockJsonNode, Map<String, String> mockFieldValues) {
        for (Map.Entry<String, String> mockFieldValue : mockFieldValues.entrySet()) {
            JsonNode fieldMock = mock(JsonNode.class);
            when(fieldMock.asText()).thenReturn(mockFieldValue.getValue());
            when(mockJsonNode.get(mockFieldValue.getKey())).thenReturn(fieldMock);
        }
        when(mockJsonNode.fieldNames()).thenReturn(mockFieldValues.keySet().iterator());
    }
}
