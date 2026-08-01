package com.petfoster.service;

import com.petfoster.common.BusinessException;
import com.petfoster.entity.FosterRequest;
import com.petfoster.entity.Pet;
import com.petfoster.entity.User;
import com.petfoster.repository.FosterRequestRepository;
import com.petfoster.repository.PetRepository;
import com.petfoster.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 实体查询与空值兜底的统一入口。
 * findXxx 返回 null（id 为 null 时也安全），getXxxOrThrow 不存在时抛业务异常，
 * username/petName/displayName 用于通知文案等场景的默认名称兜底。
 */
@Component
@RequiredArgsConstructor
public class EntityLookup {

    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final FosterRequestRepository requestRepository;

    public User findUser(Long id) {
        return id == null ? null : userRepository.findById(id).orElse(null);
    }

    public Pet findPet(Long id) {
        return id == null ? null : petRepository.findById(id).orElse(null);
    }

    public FosterRequest findRequest(Long id) {
        return id == null ? null : requestRepository.findById(id).orElse(null);
    }

    public User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }

    public Pet getPetOrThrow(Long id) {
        return petRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("宠物不存在"));
    }

    public FosterRequest getRequestOrThrow(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));
    }

    public String username(Long id, String fallback) {
        return displayName(findUser(id), fallback);
    }

    public String petName(Long id) {
        Pet pet = findPet(id);
        return pet != null ? pet.getName() : "宠物";
    }

    public static String displayName(User user, String fallback) {
        return user != null ? user.getUsername() : fallback;
    }
}
