# Hospital Management System - Patient Module Requirements

## Document Control

| Field | Value |
|-------|-------|
| Document Title | Hospital Management System - Patient Module Requirements |
| Module | Patient Management |
| Version | 1.0.0 |
| Status | Draft |
| Company | Ai Nexus |
| Created Date | February 2026 |

---

## 1. EXECUTIVE SUMMARY

The Patient Module is the foundation of the Hospital Management System. It enables hospital staff to register, search, view, and manage patient demographic information and medical history. This module will be used by receptionists for patient registration, doctors and nurses for accessing patient information, and administrators for patient data management.

**Core Value**: Centralized, secure, and efficient patient information management that serves as the foundation for all other hospital modules (appointments, EMR, billing, etc.).

---

## 2. MVP SCOPE - PATIENT MODULE

This specification covers ONLY the Patient Module with the following features:

1. **Patient Registration** - Register new patients with complete demographics
2. **Patient Search** - Search and filter patients by multiple criteria
3. **Patient Profile View** - View complete patient information
4. **Patient Update** - Update patient demographic information
5. **Patient Status Management** - Activate/deactivate patient records

**Out of Scope for This Module**:
- Authentication (will be separate Auth module)
- Appointments (separate module)
- Medical records/EMR (separate module)
- Billing (separate module)
- Pharmacy (separate module)

---

## 3. USER ROLES (For Patient Module)

| Role | Description | Patient Module Permissions |
|------|-------------|---------------------------|
| RECEPTIONIST | Front desk staff | Register, search, view, update patients |
| DOCTOR | Medical doctor | Search, view patients (read-only demographics) |
| NURSE | Nursing staff | Search, view patients (read-only demographics) |
| ADMIN | System administrator | Full access - register, search, view, update, deactivate |

**Note**: Authentication and role management will be handled by separate Auth module.

---

## 4. DETAILED REQUIREMENTS

### Requirement 1: Patient Registration

**User Story**: As a receptionist, I want to register new patients with complete demographic information, so that the hospital has accurate patient records for treatment and billing.

**Priority**: MUST HAVE (MVP)  
**Estimated Effort**: 2-3 days

**Acceptance Criteria**:

1. WHEN a receptionist clicks "Register New Patient", THE System SHALL display a patient registration form
2. WHEN the form is displayed, THE System SHALL require the following mandatory fields: first name, last name, date of birth, gender, phone number
3. WHEN the form is displayed, THE System SHALL provide optional fields: email, address, city, state, zip code, emergency contact name, emergency contact phone, emergency contact relationship, blood group, known allergies, chronic conditions
4. WHEN a receptionist enters a date of birth, THE System SHALL automatically calculate and display the patient's age
5. WHEN a receptionist enters a phone number, THE System SHALL validate it matches the format: +1-XXX-XXX-XXXX or (XXX) XXX-XXXX or XXX-XXX-XXXX
6. WHEN a receptionist enters an email, THE System SHALL validate it is a valid email format
7. WHEN a receptionist submits the form with valid data, THE System SHALL generate a unique Patient ID in format "P" + year + sequential number (e.g., P2026001)
8. WHEN a patient is successfully registered, THE System SHALL display a success message with the generated Patient ID
9. WHEN a patient is successfully registered, THE System SHALL set the patient status to "ACTIVE" by default
10. WHEN registration fails due to validation errors, THE System SHALL display specific error messages for each invalid field
11. WHEN a receptionist tries to register a patient with a phone number that already exists, THE System SHALL warn about potential duplicate but allow registration
12. WHEN a patient is registered, THE System SHALL record the registration timestamp and the user who registered the patient

---

### Requirement 2: Patient Search and Filtering

**User Story**: As a hospital staff member, I want to search for patients using multiple criteria, so that I can quickly find the patient I need to work with.

**Priority**: MUST HAVE (MVP)  
**Estimated Effort**: 2 days

**Acceptance Criteria**:

1. WHEN a user accesses the patient list page, THE System SHALL display all active patients by default
2. WHEN the patient list is displayed, THE System SHALL show: Patient ID, full name, age, gender, phone number, and status
3. WHEN the patient list has more than 20 records, THE System SHALL provide pagination with 20 patients per page
4. WHEN a user enters text in the search box, THE System SHALL search across: Patient ID, first name, last name, phone number, and email
5. WHEN a user applies the search, THE System SHALL display matching results in real-time (as user types) or on Enter key
6. WHEN a user selects a status filter (All, Active, Inactive), THE System SHALL display only patients with that status
7. WHEN a user selects a gender filter (All, Male, Female, Other), THE System SHALL display only patients with that gender
8. WHEN a user selects a blood group filter, THE System SHALL display only patients with that blood group
9. WHEN no patients match the search criteria, THE System SHALL display "No patients found" message
10. WHEN a user clicks on a patient row, THE System SHALL navigate to the patient profile page

---

### Requirement 3: Patient Profile View

**User Story**: As a hospital staff member, I want to view complete patient information in an organized layout, so that I can quickly understand the patient's demographics and medical background.

**Priority**: MUST HAVE (MVP)  
**Estimated Effort**: 2 days

**Acceptance Criteria**:

1. WHEN a user clicks on a patient from the list, THE System SHALL display the patient profile page
2. WHEN the profile page loads, THE System SHALL display patient demographics: Patient ID, full name, date of birth, age, gender, phone, email, address
3. WHEN the profile page loads, THE System SHALL display emergency contact information: name, phone, relationship
4. WHEN the profile page loads, THE System SHALL display medical information: blood group, known allergies, chronic conditions
5. WHEN the profile page loads, THE System SHALL display patient status (Active/Inactive) with color coding (green for active, red for inactive)
6. WHEN the profile page loads, THE System SHALL display registration date and registered by user
7. WHEN the profile page loads, THE System SHALL display last updated date and updated by user
8. WHEN a user with edit permissions views the profile, THE System SHALL display an "Edit Patient" button
9. WHEN a user without edit permissions views the profile, THE System SHALL NOT display the "Edit Patient" button
10. WHEN a user clicks "Back to List", THE System SHALL return to the patient list page

---

### Requirement 4: Patient Information Update

**User Story**: As a receptionist or admin, I want to update patient demographic information, so that patient records remain accurate and up-to-date.

**Priority**: MUST HAVE (MVP)  
**Estimated Effort**: 2 days

**Acceptance Criteria**:

1. WHEN a user with edit permissions clicks "Edit Patient", THE System SHALL display the patient information in an editable form
2. WHEN the edit form is displayed, THE System SHALL pre-populate all fields with current patient data
3. WHEN the edit form is displayed, THE System SHALL NOT allow editing of Patient ID (read-only)
4. WHEN the edit form is displayed, THE System SHALL NOT allow editing of registration date (read-only)
5. WHEN a user modifies any field, THE System SHALL apply the same validation rules as registration
6. WHEN a user submits the updated form with valid data, THE System SHALL save the changes
7. WHEN patient information is successfully updated, THE System SHALL display a success message
8. WHEN patient information is successfully updated, THE System SHALL record the update timestamp and the user who made the update
9. WHEN a user clicks "Cancel", THE System SHALL discard changes and return to the profile view
10. WHEN update fails due to validation errors, THE System SHALL display specific error messages for each invalid field

---

### Requirement 5: Patient Status Management

**User Story**: As an admin, I want to deactivate patient records instead of deleting them, so that we maintain historical data while marking patients who are no longer active.

**Priority**: MUST HAVE (MVP)  
**Estimated Effort**: 1 day

**Acceptance Criteria**:

1. WHEN an admin views an active patient profile, THE System SHALL display a "Deactivate Patient" button
2. WHEN an admin views an inactive patient profile, THE System SHALL display an "Activate Patient" button
3. WHEN an admin clicks "Deactivate Patient", THE System SHALL display a confirmation dialog: "Are you sure you want to deactivate this patient?"
4. WHEN an admin confirms deactivation, THE System SHALL change the patient status to "INACTIVE"
5. WHEN a patient is deactivated, THE System SHALL record the deactivation timestamp and the user who deactivated
6. WHEN a patient is deactivated, THE System SHALL display a success message
7. WHEN an admin clicks "Activate Patient", THE System SHALL change the patient status to "ACTIVE" without confirmation
8. WHEN a patient is activated, THE System SHALL record the activation timestamp and the user who activated
9. WHEN viewing the patient list with "Active" filter, THE System SHALL NOT display inactive patients
10. WHEN viewing the patient list with "All" filter, THE System SHALL display both active and inactive patients with status indicators

---

## 5. NON-FUNCTIONAL REQUIREMENTS

### Performance
- Patient search results SHALL return within 2 seconds for up to 10,000 patient records
- Patient registration SHALL complete within 3 seconds
- Patient profile page SHALL load within 2 seconds

### Security
- Patient data is Protected Health Information (PHI) and MUST be handled according to HIPAA compliance
- All access to patient data SHALL be logged for audit purposes
- Patient phone numbers and emails SHALL be stored securely
- Only authenticated users with appropriate roles SHALL access patient data

### Usability
- Patient registration form SHALL be completable in under 3 minutes
- Search functionality SHALL provide real-time feedback
- Error messages SHALL be clear and actionable
- UI SHALL be responsive and work on desktop, tablet, and mobile devices

### Data Integrity
- Patient ID SHALL be unique and auto-generated
- Phone numbers SHALL be validated before storage
- Email addresses SHALL be validated before storage
- Date of birth SHALL not allow future dates
- Age SHALL be calculated automatically from date of birth

### Scalability
- System SHALL support up to 50,000 patient records
- System SHALL support up to 100 concurrent users accessing patient data

---

## 6. OUT OF SCOPE (Not in Patient Module)

The following features are NOT part of the Patient Module and will be handled by other modules:

- User authentication and login (Auth Module)
- Appointment scheduling (Appointment Module)
- Medical records and clinical notes (EMR Module)
- Prescriptions and medications (EMR/Pharmacy Module)
- Lab results and imaging (Laboratory/Radiology Module)
- Billing and insurance (Billing Module)
- Patient portal for self-service (Patient Portal Module)
- Document upload and management (Document Module)
- Notifications and reminders (Notification Module)
- Reporting and analytics (Reporting Module)

---

## 7. ASSUMPTIONS AND DEPENDENCIES

### Assumptions
- Authentication module exists or will be built separately
- Database (PostgreSQL) is available
- Users have appropriate roles assigned (RECEPTIONIST, DOCTOR, NURSE, ADMIN)

### Dependencies
- **Auth Module**: Required for user authentication and role-based access control
- **Database**: PostgreSQL 15 or higher
- **Backend Framework**: Spring Boot 3.2.x with Java 17
- **Frontend Framework**: React 18.x

---

## 8. SUCCESS CRITERIA

The Patient Module will be considered successful when:

1. ✅ Receptionists can register new patients in under 3 minutes
2. ✅ Staff can find any patient within 5 seconds using search
3. ✅ All patient data is accurately stored and retrieved
4. ✅ Patient status can be managed (activate/deactivate)
5. ✅ All HIPAA compliance requirements are met
6. ✅ System handles 50,000 patient records without performance degradation
7. ✅ All 5 requirements are fully implemented and tested

---

**Document Version**: 1.0  
**Status**: Ready for Design Phase  
**Next Step**: Create design.md for Patient Module
