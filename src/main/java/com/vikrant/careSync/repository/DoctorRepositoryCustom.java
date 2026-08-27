package com.vikrant.careSync.repository;

import com.vikrant.careSync.dto.SearchRequestDto;
import com.vikrant.careSync.entity.Doctor;

import java.util.List;

public interface DoctorRepositoryCustom {
    List<Doctor> searchDoctorsDynamic(SearchRequestDto searchDto);
}
