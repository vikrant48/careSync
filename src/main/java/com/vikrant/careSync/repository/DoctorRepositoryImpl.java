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
import java.util.Locale;
import java.util.Set;

@Repository
public class DoctorRepositoryImpl implements DoctorRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Doctor> searchDoctorsDynamic(SearchRequestDto searchDto) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Doctor> query = cb.createQuery(Doctor.class);
        Root<Doctor> doctor = query.from(Doctor.class);
        doctor.fetch("user", JoinType.LEFT);

        List<Predicate> predicates = new ArrayList<>();

        if (searchDto != null) {
            // Text Query Search (First Name, Last Name, Email)
            if (searchDto.getQuery() != null && !searchDto.getQuery().trim().isEmpty()) {
                // Doctor has no email attribute: matching on it made every text search fail. Names only, so the
                // search cannot be used to test whether an email address belongs to a doctor.
                addNameFilter(cb, doctor, searchDto.getQuery(), predicates);
            }

            // Specialization Filter
            if (searchDto.getSpecialization() != null && !searchDto.getSpecialization().trim().isEmpty()) {
                predicates.add(cb.equal(
                        cb.lower(doctor.get("specialization")),
                        searchDto.getSpecialization().trim().toLowerCase()));
            }

            // Location / Address Filter
            if (searchDto.getLocation() != null && !searchDto.getLocation().trim().isEmpty()) {
                String locationPattern = "%" + escapeLike(searchDto.getLocation().trim().toLowerCase()) + "%";
                predicates.add(cb.like(cb.lower(doctor.get("address")), locationPattern, '\\'));
            }

            // Gender Filter
            if (searchDto.getGender() != null && !searchDto.getGender().trim().isEmpty()) {
                addGenderFilter(cb, doctor, searchDto.getGender(), predicates);
            }

            addVerifiedFilter(cb, doctor, searchDto, predicates);

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

    @Override
    public long countDoctorsDynamic(SearchRequestDto searchDto) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Doctor> doctor = query.from(Doctor.class);

        query.select(cb.count(doctor));

        List<Predicate> predicates = new ArrayList<>();

        if (searchDto != null) {
            if (searchDto.getQuery() != null && !searchDto.getQuery().trim().isEmpty()) {
                // Doctor has no email attribute: matching on it made every text search fail. Names only, so the
                // search cannot be used to test whether an email address belongs to a doctor.
                addNameFilter(cb, doctor, searchDto.getQuery(), predicates);
            }

            if (searchDto.getSpecialization() != null && !searchDto.getSpecialization().trim().isEmpty()) {
                predicates.add(cb.equal(
                        cb.lower(doctor.get("specialization")),
                        searchDto.getSpecialization().trim().toLowerCase()));
            }

            if (searchDto.getLocation() != null && !searchDto.getLocation().trim().isEmpty()) {
                String locationPattern = "%" + escapeLike(searchDto.getLocation().trim().toLowerCase()) + "%";
                predicates.add(cb.like(cb.lower(doctor.get("address")), locationPattern, '\\'));
            }

            if (searchDto.getGender() != null && !searchDto.getGender().trim().isEmpty()) {
                addGenderFilter(cb, doctor, searchDto.getGender(), predicates);
            }

            addVerifiedFilter(cb, doctor, searchDto, predicates);

            predicates.add(cb.isTrue(doctor.get("isActive")));
        } else {
            predicates.add(cb.isTrue(doctor.get("isActive")));
        }

        query.where(predicates.toArray(new Predicate[0]));

        return entityManager.createQuery(query).getSingleResult();
    }

    /** Verified filter; a doctor with no value counts as not verified (as DoctorDto shows it). */
    private static void addVerifiedFilter(CriteriaBuilder cb, Root<Doctor> doctor, SearchRequestDto searchDto,
            List<Predicate> predicates) {
        if (searchDto.getVerified() == null) {
            return;
        }
        predicates.add(searchDto.getVerified()
                ? cb.isTrue(doctor.get("isVerified"))
                : cb.or(cb.isFalse(doctor.get("isVerified")), cb.isNull(doctor.get("isVerified"))));
    }

    /** Titles people type before a name ("Dr. Sheetal Rangar"); ignored when other words are present. */
    private static final Set<String> NAME_TITLES = Set.of("dr", "doctor", "doc", "prof", "professor");

    /**
     * Every word must match the first or the last name, so a full name ("Sheetal Rangar"), a first or last name
     * alone, and a title in front ("Dr. Sheetal") all find the doctor. Matching the whole text against one name
     * column made every two-word search fail.
     */
    private static void addNameFilter(CriteriaBuilder cb, Root<Doctor> doctor, String text,
            List<Predicate> predicates) {
        for (String word : nameWords(text)) {
            String pattern = "%" + escapeLike(word) + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(doctor.get("firstName")), pattern, '\\'),
                    cb.like(cb.lower(doctor.get("lastName")), pattern, '\\')));
        }
    }

    static List<String> nameWords(String text) {
        List<String> words = new ArrayList<>();
        for (String raw : text.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            String word = raw.replaceAll("^[.,]+|[.,]+$", "");
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        List<String> withoutTitles = words.stream().filter(w -> !NAME_TITLES.contains(w)).toList();
        return withoutTitles.isEmpty() ? words : withoutTitles;
    }

    /** "female", "Female", "F" and "woman" find the same doctors, whichever way the gender was stored. */
    private static void addGenderFilter(CriteriaBuilder cb, Root<Doctor> doctor, String gender,
            List<Predicate> predicates) {
        predicates.add(cb.lower(doctor.get("gender")).in(genderValues(gender)));
    }

    static List<String> genderValues(String gender) {
        String g = gender.trim().toLowerCase(Locale.ROOT);
        return switch (g) {
            case "f", "female", "woman", "women", "lady", "ladies" -> List.of("female", "f");
            case "m", "male", "man", "men", "gentleman" -> List.of("male", "m");
            default -> List.of(g);
        };
    }

    /** User text is matched literally: % and _ in a search box are not wildcards. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
