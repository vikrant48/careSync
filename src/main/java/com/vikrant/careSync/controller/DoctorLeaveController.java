package com.vikrant.careSync.controller;

import com.vikrant.careSync.exception.ClientErrorMessages;
import com.vikrant.careSync.security.access.CurrentUserService;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.DoctorLeave;
import com.vikrant.careSync.entity.User;
import com.vikrant.careSync.service.DoctorLeaveService;
import com.vikrant.careSync.dto.DoctorLeaveDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.vikrant.careSync.util.ErrorLog;

@RestController
@RequestMapping("/api/doctor-leaves")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class DoctorLeaveController {

    private final DoctorLeaveService doctorLeaveService;
    private final CurrentUserService currentUserService;


    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> addLeave(@RequestBody Map<String, String> request) {
        try {
            Doctor currentDoctor = currentUserService.requireDoctor();
            LocalDate startDate = LocalDate.parse(request.get("startDate"));
            LocalDate endDate = LocalDate.parse(request.get("endDate"));
            String reason = request.get("reason");

            DoctorLeave leave = doctorLeaveService.addLeave(currentDoctor.getId(), startDate, endDate, reason);
            return ResponseEntity.ok(new DoctorLeaveDto(leave));
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/my-leaves")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyLeaves() {
        try {
            Doctor currentDoctor = currentUserService.requireDoctor();
            List<DoctorLeaveDto> leaves = doctorLeaveService.getDoctorLeaves(currentDoctor.getId()).stream()
                    .map(DoctorLeaveDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(leaves);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @GetMapping("/upcoming")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getUpcomingLeaves() {
        try {
            Doctor currentDoctor = currentUserService.requireDoctor();
            List<DoctorLeaveDto> leaves = doctorLeaveService.getUpcomingLeaves(currentDoctor.getId()).stream()
                    .map(DoctorLeaveDto::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(leaves);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> deleteLeave(@PathVariable Long id) {
        try {
            Doctor currentDoctor = currentUserService.requireDoctor();
            doctorLeaveService.deleteLeave(id, currentDoctor.getId());
            Map<String, String> response = new HashMap<>();
            response.put("message", "Leave record deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ErrorLog.log(e);
            return ResponseEntity.badRequest().body(ClientErrorMessages.errorBody(e));
        }
    }
}
