package com.ainexus.hospital.patient.repository;

import com.ainexus.hospital.patient.entity.HospitalUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HospitalUserRepository extends JpaRepository<HospitalUser, String> {

    Optional<HospitalUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    Page<HospitalUser> findByStatus(String status, Pageable pageable);

    Page<HospitalUser> findByRole(String role, Pageable pageable);

    Page<HospitalUser> findByStatusAndRole(String status, String role, Pageable pageable);
}
