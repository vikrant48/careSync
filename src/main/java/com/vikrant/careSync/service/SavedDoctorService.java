package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.DoctorDto;
import com.vikrant.careSync.entity.Doctor;
import com.vikrant.careSync.entity.PatientSavedDoctor;
import com.vikrant.careSync.repository.DoctorRepository;
import com.vikrant.careSync.repository.PatientSavedDoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.vikrant.careSync.util.ErrorLog;

/** A patient's saved doctors. Callers pass the logged-in patient's id; it never comes from the request. */
@Service
@RequiredArgsConstructor
public class SavedDoctorService {

    /** Keeps one account from growing the table without limit */
    static final int MAX_SAVED_DOCTORS = 200;

    private final PatientSavedDoctorRepository savedDoctorRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorService doctorService;

    /** Saved doctors, newest save first, as patients see a doctor (contact shown, private fields hidden). */
    @Transactional(readOnly = true)
    public List<DoctorDto> list(Long patientId) {
        List<Long> ids = savedDoctorRepository.findDoctorIdsByPatientId(patientId);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Doctor> byId = doctorRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Doctor::getId, Function.identity()));
        List<Doctor> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        return doctorService.convertDoctorsToDtosWithStats(ordered).stream()
                .map(dto -> dto.forViewer(false, true))
                .toList();
    }

    /** Saving a doctor that is already saved does nothing. */
    @Transactional
    public void save(Long patientId, Long doctorId) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found");
        }
        if (savedDoctorRepository.existsByPatientIdAndDoctorId(patientId, doctorId)) {
            return;
        }
        if (savedDoctorRepository.countByPatientId(patientId) >= MAX_SAVED_DOCTORS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You can save up to " + MAX_SAVED_DOCTORS + " doctors. Remove some first.");
        }
        try {
            savedDoctorRepository.saveAndFlush(new PatientSavedDoctor(patientId, doctorId));
        } catch (DataIntegrityViolationException alreadySaved) {
            ErrorLog.debug(alreadySaved);
            // Saved by a second request at the same moment: the unique key kept one row, which is the goal
        }
    }

    /** Removing a doctor that is not saved does nothing. */
    @Transactional
    public void remove(Long patientId, Long doctorId) {
        savedDoctorRepository.deleteByPatientIdAndDoctorId(patientId, doctorId);
    }
}
