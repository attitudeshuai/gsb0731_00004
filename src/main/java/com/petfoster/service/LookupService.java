package com.petfoster.service;

import com.petfoster.entity.Pet;
import com.petfoster.entity.User;
import com.petfoster.repository.PetRepository;
import com.petfoster.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 集中处理"查用户 / 查宠物 + 空值兜底"的读取逻辑。
 *
 * <p>此前各业务方法里反复出现 {@code userRepository.findById(id).orElse(null)} 紧跟着
 * {@code user != null ? user.getUsername() : "某位主人"} 这类兜底判断，散落在多个 Service。
 * 这里把它收敛为语义化的方法，兜底文案由调用方通过参数传入，行为与原来逐处的判断一致。
 */
@Service
@RequiredArgsConstructor
public class LookupService {

    private final UserRepository userRepository;
    private final PetRepository petRepository;

    public User userOrNull(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).orElse(null);
    }

    /** 返回用户名，用户不存在或为空时返回 fallback。 */
    public String usernameOr(Long userId, String fallback) {
        User user = userOrNull(userId);
        return user != null ? user.getUsername() : fallback;
    }

    public Pet petOrNull(Long petId) {
        if (petId == null) {
            return null;
        }
        return petRepository.findById(petId).orElse(null);
    }

    /** 返回宠物名，宠物不存在时返回 fallback。 */
    public String petNameOr(Long petId, String fallback) {
        Pet pet = petOrNull(petId);
        return pet != null ? pet.getName() : fallback;
    }
}
