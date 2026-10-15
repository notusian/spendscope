package com.spendscope.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "é", "界", "😀"})
    void acceptsPasswordsAtTheEncodedByteLimit(String character) {
        int bytes = character.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        String password = character.repeat(72 / bytes);

        assertThat(validator.validate(new RegisterRequest("valid@example.com", password))).isEmpty();
        assertThat(validator.validate(new LoginRequest("valid@example.com", password))).isEmpty();
        assertThat(validator.validate(new ChangePasswordRequest(password, password))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "é", "界", "😀"})
    void rejectsPasswordsOverTheEncodedByteLimitOnEveryEndpoint(String character) {
        int bytes = character.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        String password = character.repeat(72 / bytes) + "a";

        assertThat(validator.validate(new RegisterRequest("valid@example.com", password)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("password");
        assertThat(validator.validate(new LoginRequest("valid@example.com", password)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("password");
        assertThat(validator.validate(new ChangePasswordRequest(password, password)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("currentPassword", "newPassword");
    }
}
