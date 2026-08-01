package com.petfoster.common;

import com.petfoster.entity.Pet;
import com.petfoster.entity.User;
import com.petfoster.repository.PetRepository;
import com.petfoster.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EntityLoader {

    private final UserRepository userRepository;
    private final PetRepository petRepository;

    public User findUserOrNull(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).orElse(null);
    }

    public Pet findPetOrNull(Long petId) {
        if (petId == null) {
            return null;
        }
        return petRepository.findById(petId).orElse(null);
    }

    public String usernameOr(Long userId, String defaultName) {
        User user = findUserOrNull(userId);
        return user != null ? user.getUsername() : defaultName;
    }

    public String usernameOrNull(Long userId) {
        return usernameOr(userId, null);
    }

    public String petNameOr(Long petId, String defaultName) {
        Pet pet = findPetOrNull(petId);
        return pet != null ? pet.getName() : defaultName;
    }

    public String petNameOrPet(Long petId) {
        return petNameOr(petId, "宠物");
    }

    public Map<Long, User> loadUserMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    public Map<Long, Pet> loadPetMap(Collection<Long> petIds) {
        if (petIds == null || petIds.isEmpty()) {
            return Map.of();
        }
        return petRepository.findAllById(petIds).stream()
                .collect(Collectors.toMap(Pet::getId, Function.identity()));
    }
}
