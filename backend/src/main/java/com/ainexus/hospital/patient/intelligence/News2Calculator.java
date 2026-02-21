package com.ainexus.hospital.patient.intelligence;

import com.ainexus.hospital.patient.entity.PatientVitals;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless NHS NEWS2 (National Early Warning Score 2) calculator.
 *
 * <p>Scoring tables sourced from the Royal College of Physicians NEWS2 clinical guide (2017).
 * Consciousness parameter defaults to ALERT (score 0) because the AVPU field is not yet
 * present in the PatientVitals schema.
 */
@Component
public class News2Calculator {

    /**
     * Compute a NEWS2 score from the given vitals record.
     *
     * @param vitals the patient's most recent recorded vitals, or {@code null} if none exist
     * @return a fully-populated {@link News2Result}; riskLevel is {@code "NO_DATA"} when vitals is null
     */
    public News2Result compute(PatientVitals vitals) {
        if (vitals == null) {
            return noDataResult();
        }

        List<News2ComponentScore> components = new ArrayList<>();
        int total = 0;
        boolean anyThree = false;

        // --- Respiratory Rate ---
        if (vitals.getRespiratoryRate() != null) {
            int s = scoreRespiratoryRate(vitals.getRespiratoryRate());
            components.add(new News2ComponentScore(
                    "RESPIRATORY_RATE", String.valueOf(vitals.getRespiratoryRate()), s, "breaths/min", false));
            total += s;
            if (s == 3) anyThree = true;
        } else {
            components.add(new News2ComponentScore("RESPIRATORY_RATE", null, 0, "breaths/min", true));
        }

        // --- SpO2 (Scale 1) ---
        if (vitals.getOxygenSaturation() != null) {
            int s = scoreSpO2(vitals.getOxygenSaturation());
            components.add(new News2ComponentScore(
                    "SPO2", String.valueOf(vitals.getOxygenSaturation()), s, "%", false));
            total += s;
            if (s == 3) anyThree = true;
        } else {
            components.add(new News2ComponentScore("SPO2", null, 0, "%", true));
        }

        // --- Systolic Blood Pressure ---
        if (vitals.getBloodPressureSystolic() != null) {
            int s = scoreSystolicBP(vitals.getBloodPressureSystolic());
            components.add(new News2ComponentScore(
                    "SYSTOLIC_BP", String.valueOf(vitals.getBloodPressureSystolic()), s, "mmHg", false));
            total += s;
            if (s == 3) anyThree = true;
        } else {
            components.add(new News2ComponentScore("SYSTOLIC_BP", null, 0, "mmHg", true));
        }

        // --- Heart Rate ---
        if (vitals.getHeartRate() != null) {
            int s = scoreHeartRate(vitals.getHeartRate());
            components.add(new News2ComponentScore(
                    "HEART_RATE", String.valueOf(vitals.getHeartRate()), s, "bpm", false));
            total += s;
            if (s == 3) anyThree = true;
        } else {
            components.add(new News2ComponentScore("HEART_RATE", null, 0, "bpm", true));
        }

        // --- Temperature ---
        if (vitals.getTemperature() != null) {
            int s = scoreTemperature(vitals.getTemperature());
            components.add(new News2ComponentScore(
                    "TEMPERATURE", vitals.getTemperature().toPlainString(), s, "°C", false));
            total += s;
            if (s == 3) anyThree = true;
        } else {
            components.add(new News2ComponentScore("TEMPERATURE", null, 0, "°C", true));
        }

        // --- Consciousness (always ALERT = 0; AVPU not yet in vitals schema) ---
        components.add(new News2ComponentScore("CONSCIOUSNESS", "ALERT", 0, null, true));

        String riskLevel = classifyRisk(total, anyThree);
        return new News2Result(
                total,
                riskLevel,
                riskColour(riskLevel),
                recommendation(riskLevel),
                components,
                vitals.getId(),
                OffsetDateTime.now(),
                null);
    }

    // -------------------------------------------------------------------------
    // NHS scoring tables
    // -------------------------------------------------------------------------

    private int scoreRespiratoryRate(int rr) {
        if (rr <= 8)  return 3;
        if (rr <= 11) return 1;
        if (rr <= 20) return 0;
        if (rr <= 24) return 2;
        return 3; // >= 25
    }

    private int scoreSpO2(int spo2) {
        if (spo2 <= 91) return 3;
        if (spo2 <= 93) return 2;
        if (spo2 <= 95) return 1;
        return 0; // >= 96
    }

    private int scoreSystolicBP(int sbp) {
        if (sbp <= 90)  return 3;
        if (sbp <= 100) return 2;
        if (sbp <= 110) return 1;
        if (sbp <= 219) return 0;
        return 3; // >= 220
    }

    private int scoreHeartRate(int hr) {
        if (hr <= 40)  return 3;
        if (hr <= 50)  return 1;
        if (hr <= 90)  return 0;
        if (hr <= 110) return 1;
        if (hr <= 130) return 2;
        return 3; // >= 131
    }

    private int scoreTemperature(BigDecimal temp) {
        double t = temp.doubleValue();
        if (t <= 35.0) return 3;
        if (t <= 36.0) return 1;
        if (t <= 38.0) return 0;
        if (t <= 39.0) return 1;
        return 2; // >= 39.1
    }

    // -------------------------------------------------------------------------
    // Risk classification
    // -------------------------------------------------------------------------

    private String classifyRisk(int total, boolean anyThree) {
        if (total == 0) return "LOW";
        if (total <= 4) return anyThree ? "MEDIUM" : "LOW_MEDIUM";
        if (total <= 6) return "MEDIUM";
        return "HIGH"; // >= 7
    }

    private String riskColour(String riskLevel) {
        return switch (riskLevel) {
            case "LOW"        -> "green";
            case "LOW_MEDIUM" -> "yellow";
            case "MEDIUM"     -> "orange";
            case "HIGH"       -> "red";
            default           -> "unknown";
        };
    }

    private String recommendation(String riskLevel) {
        return switch (riskLevel) {
            case "LOW"        -> "Routine ward monitoring";
            case "LOW_MEDIUM" -> "Monitoring every 4\u20136 hours";
            case "MEDIUM"     -> "Urgent review within 1 hour";
            case "HIGH"       -> "Emergency clinical assessment required immediately";
            default           -> "Unknown";
        };
    }

    private News2Result noDataResult() {
        return new News2Result(
                null, "NO_DATA", null, null,
                List.of(), null,
                OffsetDateTime.now(),
                "No vitals on record");
    }
}
