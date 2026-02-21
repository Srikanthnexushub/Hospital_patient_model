package com.ainexus.hospital.patient.unit.intelligence;

import com.ainexus.hospital.patient.entity.PatientVitals;
import com.ainexus.hospital.patient.intelligence.News2Calculator;
import com.ainexus.hospital.patient.intelligence.News2ComponentScore;
import com.ainexus.hospital.patient.intelligence.News2Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the full NHS NEWS2 scoring algorithm.
 * Covers all boundary values for each parameter and all risk classification paths.
 */
class News2CalculatorTest {

    private News2Calculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new News2Calculator();
    }

    // -------------------------------------------------------------------------
    // Null vitals (NO_DATA path)
    // -------------------------------------------------------------------------

    @Test
    void compute_nullVitals_returnsNoData() {
        News2Result result = calculator.compute(null);
        assertThat(result.riskLevel()).isEqualTo("NO_DATA");
        assertThat(result.totalScore()).isNull();
        assertThat(result.message()).isEqualTo("No vitals on record");
        assertThat(result.components()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Respiratory Rate scoring
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "RR={0} → score {1}")
    @CsvSource({
            "8,  3",   // <= 8 → 3
            "9,  1",   // 9   → 1
            "11, 1",   // 11  → 1
            "12, 0",   // 12  → 0
            "20, 0",   // 20  → 0
            "21, 2",   // 21  → 2
            "24, 2",   // 24  → 2
            "25, 3",   // >= 25 → 3
            "30, 3"
    })
    void respiratoryRate_scoringBoundaries(int rr, int expectedScore) {
        PatientVitals v = allNormalVitals();
        v.setRespiratoryRate(rr);
        News2Result result = calculator.compute(v);
        News2ComponentScore rrScore = findComponent(result, "RESPIRATORY_RATE");
        assertThat(rrScore.score()).isEqualTo(expectedScore);
        assertThat(rrScore.defaulted()).isFalse();
    }

    // -------------------------------------------------------------------------
    // SpO2 scoring
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "SpO2={0} → score {1}")
    @CsvSource({
            "91, 3",   // <= 91 → 3
            "92, 2",   // 92   → 2
            "93, 2",   // 93   → 2
            "94, 1",   // 94   → 1
            "95, 1",   // 95   → 1
            "96, 0",   // >= 96 → 0
            "99, 0"
    })
    void spO2_scoringBoundaries(int spo2, int expectedScore) {
        PatientVitals v = allNormalVitals();
        v.setOxygenSaturation(spo2);
        News2Result result = calculator.compute(v);
        News2ComponentScore score = findComponent(result, "SPO2");
        assertThat(score.score()).isEqualTo(expectedScore);
        assertThat(score.defaulted()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Systolic BP scoring
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "SBP={0} → score {1}")
    @CsvSource({
            "90,  3",   // <= 90   → 3
            "91,  2",   // 91      → 2
            "100, 2",   // 100     → 2
            "101, 1",   // 101     → 1
            "110, 1",   // 110     → 1
            "111, 0",   // 111     → 0
            "219, 0",   // 219     → 0
            "220, 3",   // >= 220  → 3
            "250, 3"
    })
    void systolicBP_scoringBoundaries(int sbp, int expectedScore) {
        PatientVitals v = allNormalVitals();
        v.setBloodPressureSystolic(sbp);
        News2Result result = calculator.compute(v);
        News2ComponentScore score = findComponent(result, "SYSTOLIC_BP");
        assertThat(score.score()).isEqualTo(expectedScore);
        assertThat(score.defaulted()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Heart Rate scoring
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "HR={0} → score {1}")
    @CsvSource({
            "40,  3",   // <= 40   → 3
            "41,  1",   // 41      → 1
            "50,  1",   // 50      → 1
            "51,  0",   // 51      → 0
            "90,  0",   // 90      → 0
            "91,  1",   // 91      → 1
            "110, 1",   // 110     → 1
            "111, 2",   // 111     → 2
            "130, 2",   // 130     → 2
            "131, 3",   // >= 131  → 3
            "150, 3"
    })
    void heartRate_scoringBoundaries(int hr, int expectedScore) {
        PatientVitals v = allNormalVitals();
        v.setHeartRate(hr);
        News2Result result = calculator.compute(v);
        News2ComponentScore score = findComponent(result, "HEART_RATE");
        assertThat(score.score()).isEqualTo(expectedScore);
        assertThat(score.defaulted()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Temperature scoring
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "Temp={0} → score {1}")
    @CsvSource({
            "35.0, 3",   // <= 35.0  → 3
            "35.1, 1",   // 35.1     → 1
            "36.0, 1",   // 36.0     → 1
            "36.1, 0",   // 36.1     → 0
            "38.0, 0",   // 38.0     → 0
            "38.1, 1",   // 38.1     → 1
            "39.0, 1",   // 39.0     → 1
            "39.1, 2",   // >= 39.1  → 2
            "40.0, 2"
    })
    void temperature_scoringBoundaries(String tempStr, int expectedScore) {
        PatientVitals v = allNormalVitals();
        v.setTemperature(new BigDecimal(tempStr));
        News2Result result = calculator.compute(v);
        News2ComponentScore score = findComponent(result, "TEMPERATURE");
        assertThat(score.score()).isEqualTo(expectedScore);
        assertThat(score.defaulted()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Null vital fields default to score=0 with defaulted=true
    // -------------------------------------------------------------------------

    @Test
    void nullVitalField_defaultsToZeroScore_andSetsDefaultedTrue() {
        PatientVitals v = new PatientVitals();
        v.setId(1L);
        v.setPatientId("P2025001");
        // All vital measurement fields left null

        News2Result result = calculator.compute(v);

        assertThat(result.totalScore()).isEqualTo(0);
        assertThat(result.riskLevel()).isEqualTo("LOW");

        for (News2ComponentScore component : result.components()) {
            assertThat(component.score()).isEqualTo(0);
            assertThat(component.defaulted()).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // Risk classification
    // -------------------------------------------------------------------------

    @Test
    void allNormalVitals_returnsLowRisk() {
        News2Result result = calculator.compute(allNormalVitals());
        assertThat(result.totalScore()).isEqualTo(0);
        assertThat(result.riskLevel()).isEqualTo("LOW");
        assertThat(result.riskColour()).isEqualTo("green");
    }

    @Test
    void totalOneToFourNoSingleThree_returnsLowMedium() {
        PatientVitals v = allNormalVitals();
        v.setRespiratoryRate(21); // score=2, no single param scores 3
        News2Result result = calculator.compute(v);
        assertThat(result.totalScore()).isEqualTo(2);
        assertThat(result.riskLevel()).isEqualTo("LOW_MEDIUM");
        assertThat(result.riskColour()).isEqualTo("yellow");
    }

    @Test
    void totalOneToFourWithSingleThree_returnsMedium() {
        PatientVitals v = allNormalVitals();
        v.setRespiratoryRate(8); // score=3 → anyThree=true, total=3
        News2Result result = calculator.compute(v);
        assertThat(result.totalScore()).isEqualTo(3);
        assertThat(result.riskLevel()).isEqualTo("MEDIUM");
        assertThat(result.riskColour()).isEqualTo("orange");
    }

    @Test
    void totalFive_returnsMedium() {
        PatientVitals v = allNormalVitals();
        v.setRespiratoryRate(21);   // score=2
        v.setOxygenSaturation(94);  // score=1
        v.setBloodPressureSystolic(101); // score=1
        // total = 2+1+1 = 4; add one more
        v.setHeartRate(91);         // score=1 → total=5
        News2Result result = calculator.compute(v);
        assertThat(result.totalScore()).isEqualTo(5);
        assertThat(result.riskLevel()).isEqualTo("MEDIUM");
    }

    @Test
    void totalSevenOrMore_returnsHigh() {
        PatientVitals v = highRiskVitals();
        News2Result result = calculator.compute(v);
        assertThat(result.totalScore()).isGreaterThanOrEqualTo(7);
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.riskColour()).isEqualTo("red");
    }

    @Test
    void highRiskScenario_returnsRedColourAndEmergencyRecommendation() {
        News2Result result = calculator.compute(highRiskVitals());
        assertThat(result.riskColour()).isEqualTo("red");
        assertThat(result.recommendation()).contains("Emergency");
        assertThat(result.basedOnVitalsId()).isEqualTo(99L);
        assertThat(result.computedAt()).isBeforeOrEqualTo(OffsetDateTime.now());
    }

    @Test
    void result_containsSixComponents_includingConsciousness() {
        News2Result result = calculator.compute(allNormalVitals());
        assertThat(result.components()).hasSize(6);
        assertThat(result.components())
                .extracting(News2ComponentScore::parameter)
                .containsExactlyInAnyOrder(
                        "RESPIRATORY_RATE", "SPO2", "SYSTOLIC_BP",
                        "HEART_RATE", "TEMPERATURE", "CONSCIOUSNESS");
    }

    @Test
    void consciousnessComponent_alwaysAlertScoreZeroDefaulted() {
        News2Result result = calculator.compute(allNormalVitals());
        News2ComponentScore consciousness = findComponent(result, "CONSCIOUSNESS");
        assertThat(consciousness.value()).isEqualTo("ALERT");
        assertThat(consciousness.score()).isEqualTo(0);
        assertThat(consciousness.defaulted()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns a PatientVitals with all parameters in the normal (score=0) range. */
    private PatientVitals allNormalVitals() {
        PatientVitals v = new PatientVitals();
        v.setId(1L);
        v.setPatientId("P2025001");
        v.setRespiratoryRate(16);          // score 0 (12-20)
        v.setOxygenSaturation(98);         // score 0 (>=96)
        v.setBloodPressureSystolic(120);   // score 0 (111-219)
        v.setHeartRate(72);                // score 0 (51-90)
        v.setTemperature(new BigDecimal("37.0")); // score 0 (36.1-38.0)
        return v;
    }

    /** Returns vitals guaranteed to produce a HIGH risk score (>= 7). */
    private PatientVitals highRiskVitals() {
        PatientVitals v = new PatientVitals();
        v.setId(99L);
        v.setPatientId("P2025001");
        v.setRespiratoryRate(6);           // score 3 (<=8)
        v.setOxygenSaturation(90);         // score 3 (<=91)
        v.setBloodPressureSystolic(85);    // score 3 (<=90)
        v.setHeartRate(35);                // score 3 (<=40)
        v.setTemperature(new BigDecimal("34.5")); // score 3 (<=35.0)
        // total = 15 → HIGH
        return v;
    }

    private News2ComponentScore findComponent(News2Result result, String parameter) {
        return result.components().stream()
                .filter(c -> c.parameter().equals(parameter))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Component not found: " + parameter));
    }
}
