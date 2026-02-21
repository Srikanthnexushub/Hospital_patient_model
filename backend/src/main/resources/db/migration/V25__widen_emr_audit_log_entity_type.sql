-- Module 6: entity_type must accommodate longer values like DRUG_INTERACTION_CHECK
ALTER TABLE emr_audit_log ALTER COLUMN entity_type TYPE VARCHAR(50);
