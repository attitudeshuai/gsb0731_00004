package com.petfoster.service;

import com.petfoster.common.BusinessException;
import com.petfoster.common.PageResponse;
import com.petfoster.common.PageSort;
import com.petfoster.dto.PetDTO;
import com.petfoster.entity.Pet;
import com.petfoster.entity.User;
import com.petfoster.repository.PetRepository;
import com.petfoster.repository.UserRepository;
import com.petfoster.util.EntityCollections;
import com.petfoster.util.EntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetService {

    private static final String DEFAULT_SORT_FIELD = "createdAt";
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "name", "name",
            "age", "age",
            "species", "species",
            "createdAt", "createdAt",
            "created_at", "createdAt"
    );

    private final PetRepository petRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public PageResponse<PetDTO.PetResponse> getPets(
            int page, int size, String sort, String name, String species, Long ownerId) {

        var pageable = PageSort.of(page, size, sort, DEFAULT_SORT_FIELD, SORT_FIELDS);

        Page<Pet> petPage = petRepository.searchPets(
                StringUtils.hasText(name) ? name : null,
                StringUtils.hasText(species) ? species : null,
                ownerId,
                pageable
        );

        List<Long> ownerIds = petPage.getContent().stream()
                .map(Pet::getOwnerId)
                .distinct()
                .toList();
        Map<Long, User> userMap = EntityCollections.toIdMap(
                userRepository.findAllById(ownerIds), User::getId);

        return PageResponse.from(petPage,
                pet -> EntityMapper.toPetResponse(pet, userMap.get(pet.getOwnerId())));
    }

    public PetDTO.PetResponse getPetById(Long id) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("宠物不存在"));
        User owner = userRepository.findById(pet.getOwnerId()).orElse(null);
        return EntityMapper.toPetResponse(pet, owner);
    }

    public PageResponse<PetDTO.PetResponse> getMyPets(Long userId, int page, int size, String sort) {
        var pageable = PageSort.of(page, size, sort, DEFAULT_SORT_FIELD, SORT_FIELDS);

        Page<Pet> petPage = petRepository.findByOwnerId(userId, pageable);
        User owner = userRepository.findById(userId).orElse(null);

        return PageResponse.from(petPage, pet -> EntityMapper.toPetResponse(pet, owner));
    }

    @Transactional
    public PetDTO.PetResponse createPet(Long userId, PetDTO.CreatePetRequest request) {
        return createPet(userId, request, null);
    }

    @Transactional
    public PetDTO.PetResponse createPet(Long userId, PetDTO.CreatePetRequest request, org.springframework.web.multipart.MultipartFile photo) {
        userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        String uploadedPhotoUrl = null;
        String finalPhotoUrl = request.getPhotoUrl();

        try {
            if (photo != null && !photo.isEmpty()) {
                uploadedPhotoUrl = fileStorageService.uploadFile(photo);
                finalPhotoUrl = uploadedPhotoUrl;
                log.info("宠物照片上传成功: {}", uploadedPhotoUrl);
            }

            Pet pet = Pet.builder()
                    .ownerId(userId)
                    .name(request.getName())
                    .species(request.getSpecies())
                    .breed(request.getBreed())
                    .age(request.getAge())
                    .dietNotes(request.getDietNotes())
                    .medicalNotes(request.getMedicalNotes())
                    .dietPreference(request.getDietPreference())
                    .allergies(request.getAllergies())
                    .commonMedications(request.getCommonMedications())
                    .emergencyContact(request.getEmergencyContact())
                    .photoUrl(finalPhotoUrl)
                    .build();

            pet = petRepository.save(pet);
            log.info("宠物创建成功: petId={}, ownerId={}, photoUrl={}", pet.getId(), userId, finalPhotoUrl);

            User owner = userRepository.findById(userId).orElse(null);
            return EntityMapper.toPetResponse(pet, owner);
        } catch (Exception e) {
            if (uploadedPhotoUrl != null) {
                fileStorageService.deleteFile(uploadedPhotoUrl);
                log.warn("数据库操作失败，已清理孤儿文件: {}", uploadedPhotoUrl);
            }
            throw e;
        }
    }

    @Transactional
    public PetDTO.PetResponse updatePet(Long userId, Long petId, PetDTO.UpdatePetRequest request) {
        return updatePet(userId, petId, request, null);
    }

    @Transactional
    public PetDTO.PetResponse updatePet(Long userId, Long petId, PetDTO.UpdatePetRequest request, org.springframework.web.multipart.MultipartFile photo) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> BusinessException.notFound("宠物不存在"));

        if (!pet.getOwnerId().equals(userId)) {
            throw BusinessException.forbidden("无权限修改此宠物信息");
        }

        String oldPhotoUrl = pet.getPhotoUrl();
        String newUploadedPhotoUrl = null;

        try {
            if (StringUtils.hasText(request.getName())) {
                pet.setName(request.getName());
            }
            if (StringUtils.hasText(request.getSpecies())) {
                pet.setSpecies(request.getSpecies());
            }
            if (request.getBreed() != null) {
                pet.setBreed(request.getBreed());
            }
            if (request.getAge() != null) {
                pet.setAge(request.getAge());
            }
            if (request.getDietNotes() != null) {
                pet.setDietNotes(request.getDietNotes());
            }
            if (request.getMedicalNotes() != null) {
                pet.setMedicalNotes(request.getMedicalNotes());
            }
            if (request.getDietPreference() != null) {
                pet.setDietPreference(request.getDietPreference());
            }
            if (request.getAllergies() != null) {
                pet.setAllergies(request.getAllergies());
            }
            if (request.getCommonMedications() != null) {
                pet.setCommonMedications(request.getCommonMedications());
            }
            if (request.getEmergencyContact() != null) {
                pet.setEmergencyContact(request.getEmergencyContact());
            }
            if (request.getPhotoUrl() != null) {
                pet.setPhotoUrl(request.getPhotoUrl());
            }

            if (photo != null && !photo.isEmpty()) {
                newUploadedPhotoUrl = fileStorageService.uploadFile(photo);
                pet.setPhotoUrl(newUploadedPhotoUrl);
                log.info("宠物照片上传成功: petId={}, newPhoto={}", petId, newUploadedPhotoUrl);
            }

            pet = petRepository.save(pet);
            log.info("宠物信息更新成功: petId={}", petId);

            if (newUploadedPhotoUrl != null && StringUtils.hasText(oldPhotoUrl) && oldPhotoUrl.startsWith("/uploads/")) {
                String oldPhotoToDelete = oldPhotoUrl;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        fileStorageService.deleteFile(oldPhotoToDelete);
                        log.info("旧宠物照片已清理(事务提交后): petId={}, oldPhoto={}", petId, oldPhotoToDelete);
                    }
                });
            }

            User owner = userRepository.findById(pet.getOwnerId()).orElse(null);
            return EntityMapper.toPetResponse(pet, owner);
        } catch (Exception e) {
            if (newUploadedPhotoUrl != null) {
                fileStorageService.deleteFile(newUploadedPhotoUrl);
                log.warn("数据库操作失败，已清理孤儿文件: {}", newUploadedPhotoUrl);
            }
            throw e;
        }
    }

    @Transactional
    public void deletePet(Long userId, Long petId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> BusinessException.notFound("宠物不存在"));

        if (!pet.getOwnerId().equals(userId)) {
            throw BusinessException.forbidden("无权限删除此宠物");
        }

        String photoUrl = pet.getPhotoUrl();

        petRepository.delete(pet);
        log.info("宠物删除成功: petId={}, userId={}", petId, userId);

        if (StringUtils.hasText(photoUrl) && photoUrl.startsWith("/uploads/")) {
            fileStorageService.deleteFile(photoUrl);
            log.info("宠物照片已清理: petId={}, photoUrl={}", petId, photoUrl);
        }
    }
}
