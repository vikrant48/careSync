package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.master.*;
import com.vikrant.careSync.repository.master.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MasterDataService {
    private final GenderMasterRepository genderRepo;
    private final SpecializationMasterRepository specRepo;
    private final StatusMasterRepository statusRepo;
    private final BloodGroupMasterRepository bloodRepo;
    private final LanguageMasterRepository languageRepo;
    private final DegreeMasterRepository degreeRepo;
    private final InstitutionMasterRepository institutionRepo;
    private final HospitalMasterRepository hospitalRepo;
    private final PositionMasterRepository positionRepo;

    private static final Long DEFAULT_ORG_ID = 91L;

    public List<String> getGenders(Long orgId) {
        return genderRepo.findByOrgId(orgId != null ? orgId : DEFAULT_ORG_ID)
                .stream().map(GenderMaster::getValue).collect(Collectors.toList());
    }

    public List<String> getSpecializations(Long orgId) {
        return specRepo.findByOrgId(orgId != null ? orgId : DEFAULT_ORG_ID)
                .stream().map(SpecializationMaster::getValue).collect(Collectors.toList());
    }

    public List<String> getStatuses(Long orgId) {
        return statusRepo.findByOrgId(orgId != null ? orgId : DEFAULT_ORG_ID)
                .stream().map(StatusMaster::getValue).collect(Collectors.toList());
    }

    public List<String> getBloodGroups(Long orgId) {
        return bloodRepo.findByOrgId(orgId != null ? orgId : DEFAULT_ORG_ID)
                .stream().map(BloodGroupMaster::getValue).collect(Collectors.toList());
    }

    public List<String> getLanguages(Long orgId) {
        return languageRepo.findByOrgId(orgId != null ? orgId : DEFAULT_ORG_ID)
                .stream().map(LanguageMaster::getValue).collect(Collectors.toList());
    }

    public List<String> getDegrees(Long orgId) {
        return degreeRepo.findByOrgId(orgId != null ? orgId : DEFAULT_ORG_ID)
                .stream().map(DegreeMaster::getValue).collect(Collectors.toList());
    }

    public List<String> getInstitutions(Long orgId) {
        return institutionRepo.findByOrgId(orgId != null ? orgId : DEFAULT_ORG_ID)
                .stream().map(InstitutionMaster::getValue).collect(Collectors.toList());
    }

    public List<String> getHospitals(Long orgId) {
        return hospitalRepo.findByOrgId(orgId != null ? orgId : DEFAULT_ORG_ID)
                .stream().map(HospitalMaster::getValue).collect(Collectors.toList());
    }

    public List<String> getPositions(Long orgId) {
        return positionRepo.findByOrgId(orgId != null ? orgId : DEFAULT_ORG_ID)
                .stream().map(PositionMaster::getValue).collect(Collectors.toList());
    }
}
