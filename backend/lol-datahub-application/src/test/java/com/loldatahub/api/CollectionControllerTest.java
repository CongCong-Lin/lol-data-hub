package com.loldatahub.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionControllerTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // ── stageIds 校验规则 ──────────────────────────────────────

    @Test
    void rejectsEmptyStageIds() {
        var request = new CollectionController.CollectionRequest(1L, List.of());

        Set<ConstraintViolation<CollectionController.CollectionRequest>> violations =
                validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("stageIds"));
    }

    @Test
    void rejectsStageIdsExceeding50() {
        List<Long> stageIds = new ArrayList<>();
        for (long i = 1; i <= 51; i++) {
            stageIds.add(i);
        }
        var request = new CollectionController.CollectionRequest(1L, stageIds);

        Set<ConstraintViolation<CollectionController.CollectionRequest>> violations =
                validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("stageIds") &&
                v.getMessage().contains("50"));
    }

    @Test
    void rejectsNullElementInStageIds() {
        var request = new CollectionController.CollectionRequest(1L, Arrays.asList(1L, null, 3L));

        Set<ConstraintViolation<CollectionController.CollectionRequest>> violations =
                validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("stageIds"));
    }

    @Test
    void acceptsValidStageIds() {
        List<Long> stageIds = new ArrayList<>();
        for (long i = 1; i <= 50; i++) {
            stageIds.add(i);
        }
        var request = new CollectionController.CollectionRequest(1L, stageIds);

        Set<ConstraintViolation<CollectionController.CollectionRequest>> violations =
                validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void acceptsSingleElementStageIds() {
        var request = new CollectionController.CollectionRequest(1L, List.of(42L));

        Set<ConstraintViolation<CollectionController.CollectionRequest>> violations =
                validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsNegativeSeasonId() {
        var request = new CollectionController.CollectionRequest(-1L, List.of(1L));

        Set<ConstraintViolation<CollectionController.CollectionRequest>> violations =
                validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("seasonId"));
    }

    @Test
    void rejectsNegativeStageId() {
        var request = new CollectionController.CollectionRequest(1L, List.of(-5L));

        Set<ConstraintViolation<CollectionController.CollectionRequest>> violations =
                validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("stageIds"));
    }
}
