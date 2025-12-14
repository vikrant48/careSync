package com.vikrant.careSync.service;

import com.vikrant.careSync.dto.CreateLabTestRequest;
import com.vikrant.careSync.dto.LabTestDto;
import com.vikrant.careSync.entity.LabTest;
import com.vikrant.careSync.repository.LabTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LabTestService {

    private final LabTestRepository labTestRepository;

    public List<LabTestDto> getAllActiveLabTests() {
        return labTestRepository.findAllActiveTestsOrderByName()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<LabTestDto> getAllLabTests() {
        return labTestRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public Optional<LabTestDto> getLabTestById(Long id) {
        return labTestRepository.findById(id)
                .map(this::convertToDto);
    }

    public LabTestDto createLabTest(CreateLabTestRequest request) {
        // Check if test with same name already exists
        Optional<LabTest> existingTest = labTestRepository.findByTestNameIgnoreCase(request.getTestName());
        if (existingTest.isPresent()) {
            throw new RuntimeException("Lab test with name '" + request.getTestName() + "' already exists");
        }

        LabTest labTest = LabTest.builder()
                .testName(request.getTestName())
                .price(request.getPrice())
                .description(request.getDescription())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        LabTest savedTest = labTestRepository.save(labTest);
        return convertToDto(savedTest);
    }

    public LabTestDto updateLabTest(Long id, CreateLabTestRequest request) {
        LabTest labTest = labTestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lab test not found with id: " + id));

        // Check if another test with same name exists (excluding current test)
        Optional<LabTest> existingTest = labTestRepository.findByTestNameIgnoreCase(request.getTestName());
        if (existingTest.isPresent() && !existingTest.get().getId().equals(id)) {
            throw new RuntimeException("Lab test with name '" + request.getTestName() + "' already exists");
        }

        labTest.setTestName(request.getTestName());
        labTest.setPrice(request.getPrice());
        labTest.setDescription(request.getDescription());
        labTest.setIsActive(request.getIsActive() != null ? request.getIsActive() : labTest.getIsActive());

        LabTest updatedTest = labTestRepository.save(labTest);
        return convertToDto(updatedTest);
    }

    public void deleteLabTest(Long id) {
        LabTest labTest = labTestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lab test not found with id: " + id));

        // Soft delete by setting isActive to false
        labTest.setIsActive(false);
        labTestRepository.save(labTest);
    }

    public BigDecimal calculateTotalPrice(List<Long> testIds) {
        return labTestRepository.calculateTotalPriceByIds(testIds);
    }

    public List<LabTest> getLabTestsByIds(List<Long> testIds) {
        return labTestRepository.findAllById(testIds);
    }

    public List<LabTestDto> searchLabTestsByName(String name) {
        return labTestRepository.findByTestNameContainingIgnoreCaseAndIsActiveTrue(name)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public LabTestDto convertToDto(LabTest labTest) {
        return LabTestDto.builder()
                .id(labTest.getId())
                .testName(labTest.getTestName())
                .price(labTest.getPrice())
                .description(labTest.getDescription())
                .isActive(labTest.getIsActive())
                .createdAt(labTest.getCreatedAt())
                .updatedAt(labTest.getUpdatedAt())
                .build();
    }
}