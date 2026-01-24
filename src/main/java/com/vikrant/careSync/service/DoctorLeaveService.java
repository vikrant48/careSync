package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.DoctorLeave;
import com.vikrant.careSync.repository.DoctorLeaveRepository;
import com.vikrant.careSync.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DoctorLeaveService {

    private final DoctorLeaveRepository doctorLeaveRepository;
    private final DoctorRepository doctorRepository;

    public DoctorLeave addLeave(Long doctorId, LocalDate startDate, LocalDate endDate, String reason) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        if (startDate.isBefore(LocalDate.now())) {
            throw new RuntimeException("Leave start date cannot be in the past");
        }

        if (endDate.isBefore(startDate)) {
            throw new RuntimeException("End date cannot be before start date");
        }

        DoctorLeave leave = DoctorLeave.builder()
                .doctor(doctor)
                .startDate(startDate)
                .endDate(endDate)
                .reason(reason)
                .build();

        return doctorLeaveRepository.save(leave);
    }

    public List<DoctorLeave> getDoctorLeaves(Long doctorId) {
        return doctorLeaveRepository.findByDoctorId(doctorId);
    }

    public List<DoctorLeave> getUpcomingLeaves(Long doctorId) {
        return doctorLeaveRepository.findUpcomingLeavesByDoctor(doctorId, LocalDate.now());
    }

    public void deleteLeave(Long leaveId, Long doctorId) {
        DoctorLeave leave = doctorLeaveRepository.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave record not found"));

        if (!leave.getDoctor().getId().equals(doctorId)) {
            throw new RuntimeException("You can only delete your own leave records");
        }

        doctorLeaveRepository.delete(leave);
    }

    public boolean isDoctorOnLeave(Long doctorId, LocalDate date) {
        return doctorLeaveRepository.isDoctorOnLeave(doctorId, date);
    }

    public DoctorLeave getActiveLeave(Long doctorId, LocalDate date) {
        List<DoctorLeave> leaves = doctorLeaveRepository.findActiveLeavesByDoctorAndDate(doctorId, date);
        return leaves.isEmpty() ? null : leaves.get(0);
    }
}
