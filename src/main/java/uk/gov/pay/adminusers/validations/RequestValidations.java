package uk.gov.pay.adminusers.validations;

import com.fasterxml.jackson.databind.JsonNode;
import jersey.repackaged.com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.math.NumberUtils.isDigits;

public class RequestValidations {

    public Optional<List<String>> checkIsNumeric(JsonNode payload, String... fieldNames) {
        return applyCheck(payload, isNotNumeric(), fieldNames, "Field [%s] must be a number");
    }

    public Optional<List<String>> checkIfExists(JsonNode payload, String... fieldNames) {
        return applyCheck(payload, notExist(), fieldNames, "Field [%s] is required");
    }

    public Optional<List<String>> applyCheck(JsonNode payload, Function<JsonNode, Boolean> check, String[] fieldNames, String errorMessage) {
        List<String> errors = newArrayList();
        for (String fieldName : fieldNames) {
            if (check.apply(payload.get(fieldName))) {
                errors.add(format(errorMessage, fieldName));
            }
        }
        return errors.size() > 0 ? Optional.of(errors) : Optional.empty();
    }

    public static Function<JsonNode, Boolean> notExist() {
        return jsonElement -> (jsonElement == null || isBlank(jsonElement.asText()));
    }

    public static Function<JsonNode, Boolean> isNotNumeric() {
        return jsonNode -> !isDigits(jsonNode.asText());
    }

    public static Function<JsonNode, Boolean> isNotBoolean() {
        return jsonNode -> !ImmutableList.of("true", "false").contains(jsonNode.asText().toLowerCase());
    }
}