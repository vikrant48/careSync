package com.vikrant.careSync.config;

import com.vikrant.careSync.entity.master.*;
import com.vikrant.careSync.repository.master.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class MasterDataInitializer implements CommandLineRunner {

    private final GenderMasterRepository genderRepo;
    private final SpecializationMasterRepository specRepo;
    private final StatusMasterRepository statusRepo;
    private final BloodGroupMasterRepository bloodRepo;
    private final LanguageMasterRepository languageRepo;
    private final DegreeMasterRepository degreeRepo;
    private final InstitutionMasterRepository institutionRepo;
    private final HospitalMasterRepository hospitalRepo;
    private final PositionMasterRepository positionRepo;

    private static final Long ORG_ID = 91L;

    @Override
    public void run(String... args) throws Exception {
        seedGenders();
        seedSpecializations();
        seedStatuses();
        seedBloodGroups();
        seedLanguages();
        seedDegrees();
        seedInstitutions();
        seedHospitals();
        seedPositions();
    }

    private void seedGenders() {
        if (genderRepo.count() == 0) {
            List<String> genders = Arrays.asList("Male", "Female", "Other", "Prefer not to say");
            genders.forEach(g -> genderRepo.save(GenderMaster.builder().orgId(ORG_ID).value(g).build()));
        }
    }

    private void seedSpecializations() {
        if (specRepo.count() == 0) {
            List<String> specs = Arrays.asList(
                    "Cardiology", "Dermatology", "Endocrinology", "Gastroenterology",
                    "General Medicine", "Gynecology", "Neurology", "Oncology",
                    "Ophthalmology", "Orthopedics", "Pediatrics", "Psychiatry",
                    "Pulmonology", "Radiology", "Surgery", "Urology");
            specs.forEach(s -> specRepo.save(SpecializationMaster.builder().orgId(ORG_ID).value(s).build()));
        }
    }

    private void seedStatuses() {
        if (statusRepo.count() == 0) {
            List<String> statuses = Arrays.asList(
                    "BOOKED", "SCHEDULED", "CONFIRMED", "IN_PROGRESS",
                    "COMPLETED", "CANCELLED", "CANCELLED_BY_PATIENT", "CANCELLED_BY_DOCTOR");
            statuses.forEach(s -> statusRepo.save(StatusMaster.builder().orgId(ORG_ID).value(s).build()));
        }
    }

    private void seedBloodGroups() {
        if (bloodRepo.count() == 0) {
            List<String> groups = Arrays.asList("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
            groups.forEach(g -> bloodRepo.save(BloodGroupMaster.builder().orgId(ORG_ID).value(g).build()));
        }
    }

    private void seedLanguages() {
        if (languageRepo.count() == 0) {
            List<String> langs = Arrays.asList("Hindi", "English", "Punjabi", "Marathi", "Bengali", "Telugu", "Tamil",
                    "Others");
            langs.forEach(l -> languageRepo.save(LanguageMaster.builder().orgId(ORG_ID).value(l).build()));
        }
    }

    private void seedDegrees() {
        if (degreeRepo.count() == 0) {
            List<String> degrees = Arrays.asList(
                    "MBBS", "MD", "MS", "DM", "MCh", "DNB", "BDS", "MDS",
                    "BAMS", "BHMS", "BUMS", "BNYS", "BPT", "MPT", "PG Diploma",
                    "PhD (Medical)", "MPH", "MSc (Nursing)", "Diploma in Clinical Medicine");
            degrees.forEach(d -> degreeRepo.save(DegreeMaster.builder().orgId(ORG_ID).value(d).build()));
        }
    }

    private void seedInstitutions() {
        if (institutionRepo.count() == 0) {
            List<String> insts = Arrays.asList(
                    "AIIMS New Delhi", "PGIMER Chandigarh", "CMC Vellore", "JIPMER Puducherry",
                    "KGMU Lucknow", "IMS-BHU Varanasi", "NIMHANS Bengaluru", "AFMC Pune",
                    "MAMC New Delhi", "Grant Medical College Mumbai", "SMS Jaipur",
                    "Kasturba Medical College Manipal", "GMC Chennai", "GMCH Chandigarh",
                    "Seth GS Medical College Mumbai");
            insts.forEach(i -> institutionRepo.save(InstitutionMaster.builder().orgId(ORG_ID).value(i).build()));
        }
    }

    private void seedHospitals() {
        if (hospitalRepo.count() == 0) {
            List<String> hosps = Arrays.asList(
                    "AIIMS New Delhi", "PGIMER Chandigarh", "CMC Vellore", "JIPMER Puducherry",
                    "NIMHANS Bengaluru", "KGMU Lucknow", "IMS-BHU Varanasi", "AFMC Pune",
                    "Apollo Hospitals", "Fortis Healthcare", "Max Healthcare", "Manipal Hospitals",
                    "Tata Memorial Hospital", "Sankara Nethralaya", "Sir Ganga Ram Hospital");
            hosps.forEach(h -> hospitalRepo.save(HospitalMaster.builder().orgId(ORG_ID).value(h).build()));
        }
    }

    private void seedPositions() {
        if (positionRepo.count() == 0) {
            List<String> positions = Arrays.asList(
                    "Resident Doctor", "Senior Resident", "Consultant", "Senior Consultant",
                    "Attending Physician", "Registrar", "Medical Officer", "Surgeon",
                    "Professor", "Assistant Professor", "Associate Professor");
            positions.forEach(p -> positionRepo.save(PositionMaster.builder().orgId(ORG_ID).value(p).build()));
        }
    }
}
