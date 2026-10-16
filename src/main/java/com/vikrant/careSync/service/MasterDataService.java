package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.CreateMasterDataRequest;
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

    /** One master table: how to list, add and delete its values for an organisation. */
    private record MasterTable(java.util.function.Function<Long, List<String>> list,
            java.util.function.BiConsumer<Long, String> add,
            java.util.function.BiConsumer<Long, String> delete) {
    }

    /** Every master type with the names the API accepts for it (same aliases as before). */
    private java.util.Map<String, MasterTable> tables;

    private synchronized java.util.Map<String, MasterTable> tables() {
        if (tables == null) {
            java.util.Map<String, MasterTable> map = new java.util.HashMap<>();
            register(map, table(genderRepo::findByOrgId, GenderMaster::getValue,
                    (org, v) -> genderRepo.save(GenderMaster.builder().orgId(org).value(v).build()),
                    genderRepo::deleteByOrgIdAndValue), "genders", "gender");
            register(map, table(specRepo::findByOrgId, SpecializationMaster::getValue,
                    (org, v) -> specRepo.save(SpecializationMaster.builder().orgId(org).value(v).build()),
                    specRepo::deleteByOrgIdAndValue), "specializations", "specialization");
            register(map, table(statusRepo::findByOrgId, StatusMaster::getValue,
                    (org, v) -> statusRepo.save(StatusMaster.builder().orgId(org).value(v).build()),
                    statusRepo::deleteByOrgIdAndValue), "statuses", "status");
            register(map, table(bloodRepo::findByOrgId, BloodGroupMaster::getValue,
                    (org, v) -> bloodRepo.save(BloodGroupMaster.builder().orgId(org).value(v).build()),
                    bloodRepo::deleteByOrgIdAndValue), "bloodgroups", "blood-groups", "bloodgroup");
            register(map, table(languageRepo::findByOrgId, LanguageMaster::getValue,
                    (org, v) -> languageRepo.save(LanguageMaster.builder().orgId(org).value(v).build()),
                    languageRepo::deleteByOrgIdAndValue), "languages", "language");
            register(map, table(degreeRepo::findByOrgId, DegreeMaster::getValue,
                    (org, v) -> degreeRepo.save(DegreeMaster.builder().orgId(org).value(v).build()),
                    degreeRepo::deleteByOrgIdAndValue), "degrees", "degree");
            register(map, table(institutionRepo::findByOrgId, InstitutionMaster::getValue,
                    (org, v) -> institutionRepo.save(InstitutionMaster.builder().orgId(org).value(v).build()),
                    institutionRepo::deleteByOrgIdAndValue), "institutions", "institution");
            register(map, table(hospitalRepo::findByOrgId, HospitalMaster::getValue,
                    (org, v) -> hospitalRepo.save(HospitalMaster.builder().orgId(org).value(v).build()),
                    hospitalRepo::deleteByOrgIdAndValue), "hospitals", "hospital");
            register(map, table(positionRepo::findByOrgId, PositionMaster::getValue,
                    (org, v) -> positionRepo.save(PositionMaster.builder().orgId(org).value(v).build()),
                    positionRepo::deleteByOrgIdAndValue), "positions", "position");
            tables = map;
        }
        return tables;
    }

    private static <E> MasterTable table(java.util.function.Function<Long, List<E>> find,
            java.util.function.Function<E, String> value, java.util.function.BiConsumer<Long, String> add,
            java.util.function.BiConsumer<Long, String> delete) {
        return new MasterTable(org -> find.apply(org).stream().map(value).collect(Collectors.toList()), add, delete);
    }

    private static void register(java.util.Map<String, MasterTable> map, MasterTable table, String... names) {
        for (String name : names) {
            map.put(name, table);
        }
    }

    private MasterTable table(String masterType) {
        MasterTable table = masterType == null ? null : tables().get(masterType.toLowerCase());
        if (table == null) {
            throw new IllegalArgumentException("Unknown master data type: " + masterType);
        }
        return table;
    }

    private List<String> values(String masterType, Long orgId) {
        return table(masterType).list().apply(orgId != null ? orgId : DEFAULT_ORG_ID);
    }

    public List<String> getGenders(Long orgId) {
        return values("genders", orgId);
    }

    public List<String> getSpecializations(Long orgId) {
        return values("specializations", orgId);
    }

    public List<String> getStatuses(Long orgId) {
        return values("statuses", orgId);
    }

    public List<String> getBloodGroups(Long orgId) {
        return values("bloodgroups", orgId);
    }

    public List<String> getLanguages(Long orgId) {
        return values("languages", orgId);
    }

    public List<String> getDegrees(Long orgId) {
        return values("degrees", orgId);
    }

    public List<String> getInstitutions(Long orgId) {
        return values("institutions", orgId);
    }

    public List<String> getHospitals(Long orgId) {
        return values("hospitals", orgId);
    }

    public List<String> getPositions(Long orgId) {
        return values("positions", orgId);
    }

    public String addMasterData(String masterType, CreateMasterDataRequest request) {
        Long orgId = request.getOrgId() != null ? request.getOrgId() : DEFAULT_ORG_ID;
        String val = request.getValue().trim();
        table(masterType).add().accept(orgId, val);
        return "Added '" + val + "' to " + masterType;
    }

    public String deleteMasterData(String masterType, String value, Long orgId) {
        Long targetOrgId = orgId != null ? orgId : DEFAULT_ORG_ID;
        String val = value != null ? value.trim() : "";

        if (val.isEmpty()) {
            throw new IllegalArgumentException("Value cannot be empty for master data deletion");
        }

        table(masterType).delete().accept(targetOrgId, val);
        return "Deleted '" + val + "' from " + masterType;
    }
}
