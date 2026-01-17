package com.vikrant.careSync.repository.master;

import com.vikrant.careSync.entity.master.LanguageMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LanguageMasterRepository extends JpaRepository<LanguageMaster, Long> {
    List<LanguageMaster> findByOrgId(Long orgId);
}
