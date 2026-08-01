package com.petfoster.service;

import com.petfoster.common.BusinessException;
import com.petfoster.common.EntityLoader;
import com.petfoster.common.PageResponse;
import com.petfoster.common.PageUtils;
import com.petfoster.dto.FosterRequestDTO;
import com.petfoster.dto.ReputationDTO;
import com.petfoster.entity.FosterRequest;
import com.petfoster.entity.Notification;
import com.petfoster.entity.Pet;
import com.petfoster.entity.User;
import com.petfoster.event.NotificationEvent;
import com.petfoster.repository.FosterRequestRepository;
import com.petfoster.repository.NotificationRepository;
import com.petfoster.repository.PetRepository;
import com.petfoster.repository.UserRepository;
import com.petfoster.util.EntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FosterRequestService {

    private static final String DEFAULT_SORT_FIELD = "createdAt";
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("startDate", "endDate", "status", "createdAt");

    private final FosterRequestRepository requestRepository;
    private final PetRepository petRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final ReputationService reputationService;
    private final EntityLoader entityLoader;
    private final NotificationHelper notificationHelper;
    private final NotificationMessageFactory messageFactory;

    private static final Set<FosterRequest.Status> ALLOWED_FROM_PENDING = Set.of(
            FosterRequest.Status.Approved, FosterRequest.Status.Cancelled);
    private static final Set<FosterRequest.Status> ALLOWED_FROM_APPROVED = Set.of(
            FosterRequest.Status.InProgress, FosterRequest.Status.Cancelled);
    private static final Set<FosterRequest.Status> ALLOWED_FROM_IN_PROGRESS = Set.of(
            FosterRequest.Status.Completed, FosterRequest.Status.Cancelled);

    public PageResponse<FosterRequestDTO.RequestResponse> getRequests(
            int page, int size, String sort,
            FosterRequest.Status status, Long ownerId, Long fostererId, Long petId,
            LocalDate startDateFrom, LocalDate startDateTo, String breed) {

        Pageable pageable = PageUtils.buildPageable(page, size, sort, DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS);

        Page<FosterRequest> requestPage = requestRepository.searchRequests(
                status, ownerId, fostererId, petId,
                startDateFrom, startDateTo, breed, pageable);

        return buildPageResponse(requestPage);
    }

    public FosterRequestDTO.RequestResponse getRequestById(Long id) {
        FosterRequest request = requestRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));
        return buildSingleResponseWithReputation(request);
    }

    public PageResponse<FosterRequestDTO.RequestResponse> getMyRequests(
            Long userId, int page, int size, String sort,
            FosterRequest.Status status, LocalDate startDateFrom, LocalDate startDateTo, String breed) {

        Pageable pageable = PageUtils.buildPageable(page, size, sort, DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS);

        Page<FosterRequest> requestPage = requestRepository.findByUserIdWithFilters(
                userId, status, startDateFrom, startDateTo, breed, pageable);
        return buildPageResponse(requestPage);
    }

    public FosterRequestDTO.CheckConflictResponse checkConflict(FosterRequestDTO.CheckConflictRequest req) {
        if (req.getStartDate().isAfter(req.getEndDate())) {
            throw BusinessException.badRequest("开始日期不能晚于结束日期");
        }

        List<FosterRequest> conflicts = requestRepository.findConflictingRequests(
                req.getPetId(), req.getStartDate(), req.getEndDate(), req.getExcludeRequestId());

        List<FosterRequestDTO.ConflictInfo> conflictInfos = conflicts.stream()
                .map(r -> FosterRequestDTO.ConflictInfo.builder()
                        .requestId(r.getId())
                        .startDate(r.getStartDate())
                        .endDate(r.getEndDate())
                        .status(r.getStatus())
                        .fostererId(r.getFostererId())
                        .fostererUsername(entityLoader.usernameOrNull(r.getFostererId()))
                        .build())
                .toList();

        return FosterRequestDTO.CheckConflictResponse.builder()
                .hasConflict(!conflictInfos.isEmpty())
                .conflicts(conflictInfos)
                .build();
    }

    private void validateNoConflict(Long petId, LocalDate startDate, LocalDate endDate, Long excludeRequestId) {
        FosterRequestDTO.CheckConflictRequest checkReq = new FosterRequestDTO.CheckConflictRequest();
        checkReq.setPetId(petId);
        checkReq.setStartDate(startDate);
        checkReq.setEndDate(endDate);
        checkReq.setExcludeRequestId(excludeRequestId);

        FosterRequestDTO.CheckConflictResponse conflictResp = checkConflict(checkReq);
        if (conflictResp.isHasConflict()) {
            FosterRequestDTO.ConflictInfo firstConflict = conflictResp.getConflicts().get(0);
            throw BusinessException.badRequest(String.format(
                    "该宠物在 %s 至 %s 期间已有寄养申请（状态：%s），时间段存在冲突",
                    firstConflict.getStartDate(), firstConflict.getEndDate(),
                    messageFactory.fosterRequestStatusText(firstConflict.getStatus())));
        }
    }

    @Transactional
    public FosterRequestDTO.RequestResponse createRequest(Long userId, FosterRequestDTO.CreateRequest req) {
        Pet pet = petRepository.findById(req.getPetId())
                .orElseThrow(() -> BusinessException.notFound("宠物不存在"));

        if (!pet.getOwnerId().equals(userId)) {
            throw BusinessException.forbidden("只能为自己的宠物创建寄养申请");
        }

        if (req.getStartDate().isAfter(req.getEndDate())) {
            throw BusinessException.badRequest("开始日期不能晚于结束日期");
        }
        if (req.getEndDate().isBefore(LocalDate.now())) {
            throw BusinessException.badRequest("结束日期不能早于今天");
        }

        if (req.getFostererId() != null && req.getFostererId().equals(userId)) {
            throw BusinessException.badRequest("不能寄养自己的宠物");
        }

        validateNoConflict(req.getPetId(), req.getStartDate(), req.getEndDate(), null);

        FosterRequest request = FosterRequest.builder()
                .petId(req.getPetId())
                .ownerId(userId)
                .fostererId(req.getFostererId())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .dailyCareNotes(req.getDailyCareNotes())
                .status(FosterRequest.Status.Pending)
                .build();

        request = requestRepository.save(request);
        log.info("寄养申请创建成功: requestId={}, ownerId={}", request.getId(), userId);

        if (req.getFostererId() != null) {
            String petName = pet.getName();
            String ownerName = entityLoader.usernameOr(userId, "某位主人");
            notificationHelper.publish(
                    req.getFostererId(),
                    Notification.Type.FOSTER_REQUEST_CREATED,
                    messageFactory.fosterRequestCreatedTitle(),
                    messageFactory.fosterRequestCreatedContent(ownerName, petName, req.getStartDate(), req.getEndDate()),
                    request.getId(),
                    Notification.RelatedType.FOSTER_REQUEST
            );
        }

        return buildSingleResponse(request);
    }

    @Transactional
    public FosterRequestDTO.RequestResponse updateRequest(
            Long userId, Long requestId, FosterRequestDTO.UpdateRequest req) {

        FosterRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));

        if (!request.getOwnerId().equals(userId)) {
            throw BusinessException.forbidden("无权限修改此寄养申请");
        }

        if (request.getStatus() == FosterRequest.Status.Completed
                || request.getStatus() == FosterRequest.Status.Cancelled) {
            throw BusinessException.badRequest("已完成或已取消的申请不能修改");
        }

        if (req.getFostererId() != null) {
            if (req.getFostererId().equals(userId)) {
                throw BusinessException.badRequest("不能寄养自己的宠物");
            }
            request.setFostererId(req.getFostererId());
        }
        LocalDate newStartDate = req.getStartDate() != null ? req.getStartDate() : request.getStartDate();
        LocalDate newEndDate = req.getEndDate() != null ? req.getEndDate() : request.getEndDate();

        if (req.getStartDate() != null) {
            request.setStartDate(req.getStartDate());
        }
        if (req.getEndDate() != null) {
            request.setEndDate(req.getEndDate());
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw BusinessException.badRequest("开始日期不能晚于结束日期");
        }

        if (req.getStartDate() != null || req.getEndDate() != null) {
            validateNoConflict(request.getPetId(), newStartDate, newEndDate, requestId);
        }

        if (req.getDailyCareNotes() != null) {
            request.setDailyCareNotes(req.getDailyCareNotes());
        }

        request = requestRepository.save(request);
        log.info("寄养申请更新成功: requestId={}", requestId);

        return buildSingleResponse(request);
    }

    @Transactional
    public FosterRequestDTO.RequestResponse updateStatus(
            Long userId, Long requestId, FosterRequest.Status newStatus) {

        FosterRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));

        boolean isOwner = request.getOwnerId().equals(userId);
        boolean isFosterer = request.getFostererId() != null && request.getFostererId().equals(userId);
        if (!isOwner && !isFosterer) {
            throw BusinessException.forbidden("无权限修改此寄养申请状态");
        }

        validateStatusTransition(request.getStatus(), newStatus);

        FosterRequest.Status oldStatus = request.getStatus();
        request.setStatus(newStatus);
        request = requestRepository.save(request);
        log.info("寄养申请状态变更: requestId={}, 旧状态={}, 新状态={}",
                requestId, oldStatus, newStatus);

        String petName = entityLoader.petNameOrPet(request.getPetId());
        String operatorName = entityLoader.usernameOr(userId, "某人");

        String title = messageFactory.statusChangedTitle(newStatus);
        String content = messageFactory.statusChangedContent(operatorName, petName, newStatus);

        NotificationHelper.EntryBuilder builder = NotificationHelper.entries();
        if (!request.getOwnerId().equals(userId)) {
            builder.add(request.getOwnerId(), Notification.Type.FOSTER_REQUEST_STATUS,
                    title, content, request.getId(), Notification.RelatedType.FOSTER_REQUEST);
        }
        if (request.getFostererId() != null && !request.getFostererId().equals(userId)) {
            builder.add(request.getFostererId(), Notification.Type.FOSTER_REQUEST_STATUS,
                    title, content, request.getId(), Notification.RelatedType.FOSTER_REQUEST);
        }
        notificationHelper.publish(builder.build());

        return buildSingleResponse(request);
    }

    @Transactional
    public void deleteRequest(Long userId, Long requestId) {
        FosterRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));

        if (!request.getOwnerId().equals(userId)) {
            throw BusinessException.forbidden("无权限删除此寄养申请");
        }

        if (request.getStatus() == FosterRequest.Status.InProgress) {
            throw BusinessException.badRequest("进行中的寄养申请不能删除，请先取消");
        }

        requestRepository.delete(request);
        log.info("寄养申请删除成功: requestId={}, userId={}", requestId, userId);
    }

    private void validateStatusTransition(FosterRequest.Status current, FosterRequest.Status next) {
        boolean valid = switch (current) {
            case Pending -> ALLOWED_FROM_PENDING.contains(next);
            case Approved -> ALLOWED_FROM_APPROVED.contains(next);
            case InProgress -> ALLOWED_FROM_IN_PROGRESS.contains(next);
            case Completed, Cancelled -> false;
        };
        if (!valid) {
            throw BusinessException.badRequest(
                    String.format("无法从状态 %s 变更为 %s", current, next));
        }
    }

    private PageResponse<FosterRequestDTO.RequestResponse> buildPageResponse(Page<FosterRequest> page) {
        List<Long> petIds = page.getContent().stream()
                .map(FosterRequest::getPetId).distinct().toList();
        Set<Long> userIds = page.getContent().stream()
                .flatMap(r -> Stream.of(r.getOwnerId(), r.getFostererId()))
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());

        Map<Long, Pet> petMap = entityLoader.loadPetMap(petIds);
        Map<Long, User> userMap = entityLoader.loadUserMap(userIds);

        List<FosterRequestDTO.RequestResponse> content = page.getContent().stream()
                .map(r -> EntityMapper.toFosterRequestResponse(
                        r,
                        petMap.get(r.getPetId()),
                        userMap.get(r.getOwnerId()),
                        r.getFostererId() != null ? userMap.get(r.getFostererId()) : null
                ))
                .toList();

        return PageUtils.toPageResponse(page, content);
    }

    private FosterRequestDTO.RequestResponse buildSingleResponse(FosterRequest r) {
        return EntityMapper.toFosterRequestResponse(
                r,
                entityLoader.findPetOrNull(r.getPetId()),
                entityLoader.findUserOrNull(r.getOwnerId()),
                entityLoader.findUserOrNull(r.getFostererId())
        );
    }

    private FosterRequestDTO.RequestResponse buildSingleResponseWithReputation(FosterRequest r) {
        Pet pet = entityLoader.findPetOrNull(r.getPetId());
        User owner = entityLoader.findUserOrNull(r.getOwnerId());
        User fosterer = entityLoader.findUserOrNull(r.getFostererId());

        ReputationDTO ownerReputation = reputationService.calculateReputation(r.getOwnerId());
        ReputationDTO fostererReputation = r.getFostererId() != null
                ? reputationService.calculateReputation(r.getFostererId()) : null;

        return EntityMapper.toFosterRequestResponse(r, pet, owner, fosterer, ownerReputation, fostererReputation);
    }

    @Transactional
    public int cancelExpiredApprovedRequests(int timeoutDays) {
        LocalDate threshold = LocalDate.now().minusDays(timeoutDays);
        List<FosterRequest> expiredRequests = requestRepository.findApprovedBeforeDate(threshold);

        for (FosterRequest request : expiredRequests) {
            request.setStatus(FosterRequest.Status.Cancelled);
            requestRepository.save(request);
            log.info("寄养申请超时自动取消: requestId={}, startDate={}, 超时天数={}",
                    request.getId(), request.getStartDate(), timeoutDays);

            String petName = entityLoader.petNameOrPet(request.getPetId());
            String title = messageFactory.requestTimeoutTitle();
            String content = messageFactory.requestTimeoutContent(petName, timeoutDays, request.getStartDate());

            NotificationHelper.EntryBuilder builder = NotificationHelper.entries();
            builder.add(request.getOwnerId(), Notification.Type.FOSTER_REQUEST_TIMEOUT,
                    title, content, request.getId(), Notification.RelatedType.FOSTER_REQUEST);
            if (request.getFostererId() != null) {
                builder.add(request.getFostererId(), Notification.Type.FOSTER_REQUEST_TIMEOUT,
                        title, content, request.getId(), Notification.RelatedType.FOSTER_REQUEST);
            }
            notificationHelper.publish(builder.build());
        }

        return expiredRequests.size();
    }

    @Transactional
    public int sendReturnReminders(int daysBefore) {
        LocalDate endDate = LocalDate.now().plusDays(daysBefore);
        List<FosterRequest> requests = requestRepository.findInProgressEndingOnDate(endDate);

        int reminderCount = 0;
        for (FosterRequest request : requests) {
            String petName = entityLoader.petNameOrPet(request.getPetId());

            String title = messageFactory.returnReminderTitle(daysBefore);
            String content = messageFactory.returnReminderContent(petName, request.getEndDate());

            NotificationHelper.EntryBuilder builder = NotificationHelper.entries();

            boolean ownerReminded = notificationRepository.existsByUserIdAndTypeAndRelatedIdAndRelatedType(
                    request.getOwnerId(),
                    Notification.Type.FOSTER_RETURN_REMINDER,
                    request.getId(),
                    Notification.RelatedType.FOSTER_REQUEST
            );
            if (!ownerReminded) {
                builder.add(request.getOwnerId(), Notification.Type.FOSTER_RETURN_REMINDER,
                        title, content, request.getId(), Notification.RelatedType.FOSTER_REQUEST);
            }

            if (request.getFostererId() != null) {
                boolean fostererReminded = notificationRepository.existsByUserIdAndTypeAndRelatedIdAndRelatedType(
                        request.getFostererId(),
                        Notification.Type.FOSTER_RETURN_REMINDER,
                        request.getId(),
                        Notification.RelatedType.FOSTER_REQUEST
                );
                if (!fostererReminded) {
                    builder.add(request.getFostererId(), Notification.Type.FOSTER_RETURN_REMINDER,
                            title, content, request.getId(), Notification.RelatedType.FOSTER_REQUEST);
                }
            }

            if (!builder.isEmpty()) {
                List<NotificationEvent.NotificationEntry> entries = builder.build();
                notificationHelper.publish(entries);
                reminderCount += entries.size();
                log.info("寄养归还提醒已发送: requestId={}, 提前天数={}, 接收人数={}",
                        request.getId(), daysBefore, entries.size());
            }
        }

        return reminderCount;
    }
}
