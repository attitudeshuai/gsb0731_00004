package com.petfoster.repository;

import com.petfoster.entity.Pet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PetRepository extends JpaRepository<Pet, Long> {
    List<Pet> findByOwnerId(Long ownerId);

    Page<Pet> findByOwnerId(Long ownerId, Pageable pageable);

    @Query("SELECT p FROM Pet p WHERE " +
           "(:name IS NULL OR p.name LIKE %:name%) AND " +
           "(:species IS NULL OR p.species = :species) AND " +
           "(:ownerId IS NULL OR p.ownerId = :ownerId)")
    Page<Pet> searchPets(
            @Param("name") String name,
            @Param("species") String species,
            @Param("ownerId") Long ownerId,
            Pageable pageable
    );

    @Query("SELECT p.species AS species, COUNT(p) AS cnt FROM Pet p GROUP BY p.species")
    List<SpeciesCount> countBySpecies();

    interface SpeciesCount {
        String getSpecies();
        Long getCnt();
    }

    @Query("SELECT COALESCE(p.breed, '') AS breed, p.species AS species, COUNT(r) AS cnt " +
           "FROM FosterRequest r JOIN Pet p ON r.petId = p.id " +
           "GROUP BY COALESCE(p.breed, ''), p.species")
    List<BreedRequestCount> countRequestsByBreed();

    interface BreedRequestCount {
        String getBreed();
        String getSpecies();
        Long getCnt();
    }
}
