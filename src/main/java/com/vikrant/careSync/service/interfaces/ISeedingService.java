package com.vikrant.careSync.service.interfaces;

import com.vikrant.careSync.dto.SeedUserRequest;
import com.vikrant.careSync.dto.SeedBulkResponse;

import java.util.List;

public interface ISeedingService {

    /**
     * Seed a single user (PATIENT, DOCTOR, or ADMIN) into users and role-specific
     * tables with automatic field encryption.
     * 
     * @param request SeedUserRequest payload
     * @return Created entity (Patient, Doctor, or User)
     */
    Object seedSingleUser(SeedUserRequest request);

    /**
     * Seed bulk users into users and role-specific tables with automatic field
     * encryption using a custom JSON request list.
     * 
     * @param requests        List of custom SeedUserRequest payloads
     * @param defaultPassword Default raw password for generated users if not
     *                        specified per item (defaults to 'demo')
     * @return SeedBulkResponse summary of execution details
     */
    SeedBulkResponse seedBulkUsers(List<SeedUserRequest> requests, String defaultPassword);
}
