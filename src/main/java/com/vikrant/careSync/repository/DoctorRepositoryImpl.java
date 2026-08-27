package com.vikrant.careSync.repository;

import com.vikrant.careSync.dto.SearchRequestDto;
import com.vikrant.careSync.entity.Doctor;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class DoctorRepositoryImpl implements DoctorRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Doctor> searchDoctorsDynamic(SearchRequestDto searchDto) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Doctor> query = cb.createQuery(Doctor.class);
        Root<Doctor> doctor = query.from(Doctor.class);

        List<Predicate> predicates = new ArrayList<>();

        if (searchDto != null) {
            // Text Query Search (First Name, Last Name, Email)
            if (searchDto.getQuery() != null && !searchDto.getQuery().trim().isEmpty()) {
                String pattern = "%" + searchDto.getQuery().trim().toLowerCase() + "%";
                Predicate firstNameMatch = cb.like(cb.lower(doctor.get("firstName")), pattern);
                Predicate lastNameMatch = cb.like(cb.lower(doctor.get("lastName")), pattern);
                Predicate emailMatch = cb.like(cb.lower(doctor.get("email")), pattern);
                predicates.add(cb.or(firstNameMatch, lastNameMatch, emailMatch));
            }

            // Specialization Filter
            if (searchDto.getSpecialization() != null && !searchDto.getSpecialization().trim().isEmpty()) {
                predicates.add(cb.equal(
                        cb.lower(doctor.get("specialization")),
                        searchDto.getSpecialization().trim().toLowerCase()));
            }

            // Location / Address Filter
            if (searchDto.getLocation() != null && !searchDto.getLocation().trim().isEmpty()) {
                String locationPattern = "%" + searchDto.getLocation().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(doctor.get("address")), locationPattern));
            }

            // Active status filter (only return active doctors if flag exists)
            predicates.add(cb.isTrue(doctor.get("isActive")));
        } else {
            predicates.add(cb.isTrue(doctor.get("isActive")));
        }

        query.where(predicates.toArray(new Predicate[0]));

        // Dynamic Sorting
        if (searchDto != null && searchDto.getSortBy() != null && !searchDto.getSortBy().trim().isEmpty()) {
            String sortBy = searchDto.getSortBy().trim();
            boolean isAscending = searchDto.getSortDirection() == null
                    || !"desc".equalsIgnoreCase(searchDto.getSortDirection());

            Path<?> sortPath = switch (sortBy) {
                case "firstName" -> doctor.get("firstName");
                case "lastName" -> doctor.get("lastName");
                case "specialization" -> doctor.get("specialization");
                case "createdAt" -> doctor.get("createdAt");
                default -> doctor.get("id");
            };

            query.orderBy(isAscending ? cb.asc(sortPath) : cb.desc(sortPath));
        } else {
            query.orderBy(cb.asc(doctor.get("id")));
        }

        TypedQuery<Doctor> typedQuery = entityManager.createQuery(query);

        // Pagination if specified
        if (searchDto != null && searchDto.getPage() != null && searchDto.getSize() != null
                && searchDto.getSize() > 0) {
            int pageNumber = Math.max(0, searchDto.getPage());
            typedQuery.setFirstResult(pageNumber * searchDto.getSize());
            typedQuery.setMaxResults(searchDto.getSize());
        }

        return typedQuery.getResultList();
    }
}
