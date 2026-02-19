package com.ainexus.hospital.patient.unit.validation;

import com.ainexus.hospital.patient.validation.PhoneNumberValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberValidatorTest {

    private PhoneNumberValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PhoneNumberValidator();
    }

    // ── Valid formats ───────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "+1-555-123-4567",
            "+1-000-000-0000",
            "+1-999-999-9999",
    })
    void validInternationalFormat(String phone) {
        assertThat(validator.isValid(phone, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(555) 123-4567",
            "(000) 000-0000",
            "(999) 999-9999",
    })
    void validParenthesisFormat(String phone) {
        assertThat(validator.isValid(phone, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "555-123-4567",
            "000-000-0000",
            "999-999-9999",
    })
    void validDashFormat(String phone) {
        assertThat(validator.isValid(phone, null)).isTrue();
    }

    @Test
    void nullValue_isValid() {
        // @NotNull is a separate concern — null is valid from this validator's perspective
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void blankValue_isValid() {
        // @NotBlank is a separate concern
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid("   ", null)).isTrue();
    }

    // ── Invalid formats ─────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "12345",
            "555-12-456",
            "5551234567",
            "abc-def-ghij",
            "+1-555-123",          // too short
            "1-555-123-4567",      // missing + prefix
            "(555)123-4567",       // missing space after )
            "555 123 4567",        // spaces instead of dashes
            "+44-555-123-4567",    // non-US country code
    })
    void invalidPhone_returnsFalse(String phone) {
        assertThat(validator.isValid(phone, null)).isFalse();
    }
}
