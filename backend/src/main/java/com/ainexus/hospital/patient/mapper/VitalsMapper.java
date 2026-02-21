package com.ainexus.hospital.patient.mapper;

import com.ainexus.hospital.patient.dto.response.VitalsResponse;
import com.ainexus.hospital.patient.entity.PatientVitals;
import org.springframework.stereotype.Component;

@Component
public class VitalsMapper {

    public VitalsResponse toResponse(PatientVitals entity) {
        return new VitalsResponse(
                entity.getId(),
                entity.getAppointmentId(),
                entity.getPatientId(),
                entity.getBloodPressureSystolic(),
                entity.getBloodPressureDiastolic(),
                entity.getHeartRate(),
                entity.getTemperature(),
                entity.getWeight(),
                entity.getHeight(),
                entity.getOxygenSaturation(),
                entity.getRespiratoryRate(),
                entity.getRecordedBy(),
                entity.getRecordedAt()
        );
    }
}
