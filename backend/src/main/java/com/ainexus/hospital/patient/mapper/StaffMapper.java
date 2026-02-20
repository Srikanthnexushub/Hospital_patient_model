package com.ainexus.hospital.patient.mapper;

import com.ainexus.hospital.patient.dto.response.UserDetailResponse;
import com.ainexus.hospital.patient.dto.response.UserProfileResponse;
import com.ainexus.hospital.patient.dto.response.UserSummaryResponse;
import com.ainexus.hospital.patient.entity.HospitalUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StaffMapper {

    /** Maps to detailed admin response — no password_hash field in the record. */
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "department", source = "department")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "lastLoginAt", source = "lastLoginAt")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "failedAttempts", source = "failedAttempts")
    UserDetailResponse toDetailResponse(HospitalUser user);

    /** Maps to summary list response — no password_hash field in the record. */
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "department", source = "department")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "lastLoginAt", source = "lastLoginAt")
    UserSummaryResponse toSummaryResponse(HospitalUser user);

    /** Maps to own-profile response for /auth/me — no sensitive fields. */
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "department", source = "department")
    @Mapping(target = "lastLoginAt", source = "lastLoginAt")
    UserProfileResponse toProfileResponse(HospitalUser user);
}
