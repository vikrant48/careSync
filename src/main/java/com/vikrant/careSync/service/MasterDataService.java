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

    public String addMasterData(String masterType, com.vikrant.careSync.dto.CreateMasterDataRequest request) {
        Long orgId = request.getOrgId() != null ? request.getOrgId() : DEFAULT_ORG_ID;
        String val = request.getValue().trim();

        switch (masterType.toLowerCase()) {
            case "genders":
            case "gender": {
                GenderMaster gm = GenderMaster.builder().orgId(orgId).value(val).build();
                genderRepo.save(gm);
                break;
            }
            case "specializations":
            case "specialization": {
                SpecializationMaster sm = SpecializationMaster.builder().orgId(orgId).value(val).build();
                specRepo.save(sm);
                break;
            }
            case "statuses":
            case "status": {
                StatusMaster stm = StatusMaster.builder().orgId(orgId).value(val).build();
                statusRepo.save(stm);
                break;
            }
            case "bloodgroups":
            case "blood-groups":
            case "bloodgroup": {
                BloodGroupMaster bgm = BloodGroupMaster.builder().orgId(orgId).value(val).build();
                bloodRepo.save(bgm);
                break;
            }
            case "languages":
            case "language": {
                LanguageMaster lm = LanguageMaster.builder().orgId(orgId).value(val).build();
                languageRepo.save(lm);
                break;
            }
            case "degrees":
            case "degree": {
                DegreeMaster dm = DegreeMaster.builder().orgId(orgId).value(val).build();
                degreeRepo.save(dm);
                break;
            }
            case "institutions":
            case "institution": {
                InstitutionMaster im = InstitutionMaster.builder().orgId(orgId).value(val).build();
                institutionRepo.save(im);
                break;
            }
            case "hospitals":
            case "hospital": {
                HospitalMaster hm = HospitalMaster.builder().orgId(orgId).value(val).build();
                hospitalRepo.save(hm);
                break;
            }
            case "positions":
            case "position": {
                PositionMaster pm = PositionMaster.builder().orgId(orgId).value(val).build();
                positionRepo.save(pm);
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown master data type: " + masterType);
        }
        return "Added '" + val + "' to " + masterType;
    }
}
