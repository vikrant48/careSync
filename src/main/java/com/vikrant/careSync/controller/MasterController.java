package com.vikrant.careSync.controller;

import com.vikrant.careSync.service.MasterDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/master")
@RequiredArgsConstructor
public class MasterController {
    private final MasterDataService masterDataService;

    @GetMapping("/all")
    public ResponseEntity<Map<String, List<String>>> getAllMasterData(@RequestParam(required = false) Long orgId) {
        Map<String, List<String>> response = new HashMap<>();
        response.put("genders", masterDataService.getGenders(orgId));
        response.put("specializations", masterDataService.getSpecializations(orgId));
        response.put("statuses", masterDataService.getStatuses(orgId));
        response.put("bloodGroups", masterDataService.getBloodGroups(orgId));
        response.put("languages", masterDataService.getLanguages(orgId));
        response.put("degrees", masterDataService.getDegrees(orgId));
        response.put("institutions", masterDataService.getInstitutions(orgId));
        response.put("hospitals", masterDataService.getHospitals(orgId));
        response.put("positions", masterDataService.getPositions(orgId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/genders")
    public ResponseEntity<List<String>> getGenders(@RequestParam(required = false) Long orgId) {
        return ResponseEntity.ok(masterDataService.getGenders(orgId));
    }

    @GetMapping("/specializations")
    public ResponseEntity<List<String>> getSpecializations(@RequestParam(required = false) Long orgId) {
        return ResponseEntity.ok(masterDataService.getSpecializations(orgId));
    }

    @GetMapping("/statuses")
    public ResponseEntity<List<String>> getStatuses(@RequestParam(required = false) Long orgId) {
        return ResponseEntity.ok(masterDataService.getStatuses(orgId));
    }

    @GetMapping("/blood-groups")
    public ResponseEntity<List<String>> getBloodGroups(@RequestParam(required = false) Long orgId) {
        return ResponseEntity.ok(masterDataService.getBloodGroups(orgId));
    }

    @GetMapping("/languages")
    public ResponseEntity<List<String>> getLanguages(@RequestParam(required = false) Long orgId) {
        return ResponseEntity.ok(masterDataService.getLanguages(orgId));
    }

    @GetMapping("/degrees")
    public ResponseEntity<List<String>> getDegrees(@RequestParam(required = false) Long orgId) {
        return ResponseEntity.ok(masterDataService.getDegrees(orgId));
    }

    @GetMapping("/institutions")
    public ResponseEntity<List<String>> getInstitutions(@RequestParam(required = false) Long orgId) {
        return ResponseEntity.ok(masterDataService.getInstitutions(orgId));
    }

    @GetMapping("/hospitals")
    public ResponseEntity<List<String>> getHospitals(@RequestParam(required = false) Long orgId) {
        return ResponseEntity.ok(masterDataService.getHospitals(orgId));
    }

    @GetMapping("/positions")
    public ResponseEntity<List<String>> getPositions(@RequestParam(required = false) Long orgId) {
        return ResponseEntity.ok(masterDataService.getPositions(orgId));
    }
}
