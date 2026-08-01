package com.petfoster.service;

import com.petfoster.common.BusinessException;
import com.petfoster.common.PageResponse;
import com.petfoster.dto.FosterRequestDTO;
import com.petfoster.dto.ReputationDTO;
import com.petfoster.entity.FosterRequest;
import com.petfoster.entity.Notification;
import com.petfoster.entity.Pet;
import com.petfoster.entity.User;
import com.petfoster.event.NotificationEvent;
import com.petfoster.event.NotificationPublisher;
import com.petfoster.repository.FosterRequestRepository;
import com.petfoster.repository.NotificationRepository;
import com.petfoster.repository.PetRepository;
import com.petfoster.repository.UserRepository;
import com.petfoster.util.EntityMapper;
import com.petfoster.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FosterRequestService {

    private final FosterRequestRepository requestRepository;
    private final PetRepository petRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPublisher notificationPublisher;
    private final ReputationService reputationService;
    private final EntityLookup entityLookup;

    private static final Map<String, String> ALLOWED_SORT_FIELDS = Map.of(
            "startDate", "startDate",
            "start_date", "startDate",
            "endDate", "endDate",
            "end_date", "endDate",
            "status", "status",
            "createdAt", "createdAt",
            "created_at", "createdAt"
    );
    private static final String DEFAULT_SORT_FIELD = "createdAt";

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

        Pageable pageable = PageUtils.pageable(page, size, sort, ALLOWED_SORT_FIELDS, DEFAULT_SORT_FIELD);

        Page<FosterRequest> requestPage = requestRepository.searchRequests(
                status, ownerId, fostererId, petId,
                startDateFrom, startDateTo, breed, pageable);

        return buildPageResponse(requestPage);
    }

    public FosterRequestDTO.RequestResponse getRequestById(Long id) {
        FosterRequest request = entityLookup.getRequestOrThrow(id);
        return buildSingleResponseWithReputation(request);
    }

    public PageResponse<FosterRequestDTO.RequestResponse> getMyRequests(
            Long userId, int page, int size, String sort,
            FosterRequest.Status status, LocalDate startDateFrom, LocalDate startDateTo, String breed) {

        Pageable pageable = PageUtils.pageable(page, size, sort, ALLOWED_SORT_FIELDS, DEFAULT_SORT_FIELD);

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
                        .fostererUsername(entityLookup.username(r.getFostererId(), null))
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
                    getStatusText(firstConflict.getStatus())));
        }
    }

    @Transactional
    public FosterRequestDTO.RequestResponse createRequest(Long userId, FosterRequestDTO.CreateRequest req) {
        Pet pet = entityLookup.getPetOrThrow(req.getPetId());

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

        FosterRequest.Status initialStatus = req.getFostererId() != null
                ? FosterRequest.Status.Pending : FosterRequest.Status.Pending;

        FosterRequest request = FosterRequest.builder()
                .petId(req.getPetId())
                .ownerId(userId)
                .fostererId(req.getFostererId())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .dailyCareNotes(req.getDailyCareNotes())
                .status(initialStatus)
                .build();

        request = requestRepository.save(request);
        log.info("寄养申请创建成功: requestId={}, ownerId={}", request.getId(), userId);

        if (req.getFostererId() != null) {
            String ownerName = entityLookup.username(userId, "某位主人");
            notificationPublisher.notify(
                    req.getFostererId(),
                    Notification.Type.FOSTER_REQUEST_CREATED,
                    "收到新的寄养申请",
                    String.format("%s 邀请您帮忙寄养宠物「%s」，寄养时间：%s 至 %s",
                            ownerName, pet.getName(), req.getStartDate(), req.getEndDate()),
                    request.getId(),
                    Notification.RelatedType.FOSTER_REQUEST
            );
        }

        return buildSingleResponse(request);
    }

    @Transactional
    public FosterRequestDTO.RequestResponse updateRequest(
            Long userId, Long requestId, FosterRequestDTO.UpdateRequest req) {

        FosterRequest request = entityLookup.getRequestOrThrow(requestId);

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

        FosterRequest request = entityLookup.getRequestOrThrow(requestId);

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

        String petName = entityLookup.petName(request.getPetId());
        String operatorName = entityLookup.username(userId, "某人");

        String statusText = getStatusText(newStatus);
        String title = String.format("寄养申请状态变更：%s", statusText);
        String content = String.format("%s 将宠物「%s」的寄养申请状态更新为「%s」",
                operatorName, petName, statusText);

        notificationPublisher.notifyParties(
                request.getOwnerId(), request.getFostererId(), userId,
                Notification.Type.FOSTER_REQUEST_STATUS,
                title, content,
                request.getId(),
                Notification.RelatedType.FOSTER_REQUEST
        );

        return buildSingleResponse(request);
    }

    @Transactional
    public void deleteRequest(Long userId, Long requestId) {
        FosterRequest request = entityLookup.getRequestOrThrow(requestId);

        if (!request.getOwnerId().equals(userId)) {
            throw BusinessException.forbidden("无权限删除此寄养申请");
        }

        if (request.getStatus() == FosterRequest.Status.InProgress) {
            throw BusinessException.badRequest("进行中的寄养申请不能删除，请先取消");
        }

        requestRepository.delete(request);
        log.info("寄养申请删除成功: requestId={}, userId={}", requestId, userId);
    }

    private String getStatusText(FosterRequest.Status status) {
        return switch (status) {
            case Pending -> "待确认";
            case Approved -> "已同意";
            case InProgress -> "进行中";
            case Completed -> "已完成";
            case Cancelled -> "已取消";
        };
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
                .collect(Collectors.toSet());

        Map<Long, Pet> petMap = petRepository.findAllById(petIds).stream()
                .collect(Collectors.toMap(Pet::getId, p -> p));
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<FosterRequestDTO.RequestResponse> content = page.getContent().stream()
                .map(r -> EntityMapper.toFosterRequestResponse(
                        r,
                        petMap.get(r.getPetId()),
                        userMap.get(r.getOwnerId()),
                        r.getFostererId() != null ? userMap.get(r.getFostererId()) : null
                ))
                .toList();

        return PageResponse.of(page, content);
    }

    private FosterRequestDTO.RequestResponse buildSingleResponse(FosterRequest r) {
        return EntityMapper.toFosterRequestResponse(
                r,
                entityLookup.findPet(r.getPetId()),
                entityLookup.findUser(r.getOwnerId()),
                entityLookup.findUser(r.getFostererId()));
    }

    private FosterRequestDTO.RequestResponse buildSingleResponseWithReputation(FosterRequest r) {
        ReputationDTO ownerReputation = reputationService.calculateReputation(r.getOwnerId());
        ReputationDTO fostererReputation = r.getFostererId() != null
                ? reputationService.calculateReputation(r.getFostererId()) : null;

        return EntityMapper.toFosterRequestResponse(
                r,
                entityLookup.findPet(r.getPetId()),
                entityLookup.findUser(r.getOwnerId()),
                entityLookup.findUser(r.getFostererId()),
                ownerReputation, fostererReputation);
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

            Pet pet = entityLookup.findPet(request.getPetId());
            String petName = pet != null ? pet.getName() : "宠物";
            String title = "寄养申请超时自动取消";
            String content = String.format(
                    "宠物「%s」的寄养申请因批准后超过 %d 天未开始，已自动取消（寄养开始日期：%s）",
                    petName, timeoutDays, request.getStartDate());

            notificationPublisher.notifyParties(
                    request.getOwnerId(), request.getFostererId(), null,
                    Notification.Type.FOSTER_REQUEST_TIMEOUT,
                    title, content,
                    request.getId(),
                    Notification.RelatedType.FOSTER_REQUEST
            );
        }

        return expiredRequests.size();
    }

    @Transactional
    public int sendReturnReminders(int daysBefore) {
        LocalDate endDate = LocalDate.now().plusDays(daysBefore);
        List<FosterRequest> requests = requestRepository.findInProgressEndingOnDate(endDate);

        int reminderCount = 0;
        for (FosterRequest request : requests) {
            String petName = entityLookup.petName(request.getPetId());

            String title = String.format("寄养归还提醒：还有 %d 天", daysBefore);
            String content = String.format(
                    "宠物「%s」的寄养将于 %s 到期，请记得按时归还宠物，感谢您的配合！",
                    petName, request.getEndDate());

            List<NotificationEvent.NotificationEntry> entries = new ArrayList<>();

            boolean ownerReminded = notificationRepository.existsByUserIdAndTypeAndRelatedIdAndRelatedType(
                    request.getOwnerId(),
                    Notification.Type.FOSTER_RETURN_REMINDER,
                    request.getId(),
                    Notification.RelatedType.FOSTER_REQUEST
            );
            if (!ownerReminded) {
                entries.add(NotificationEvent.entry(
                        request.getOwnerId(),
                        Notification.Type.FOSTER_RETURN_REMINDER,
                        title, content,
                        request.getId(),
                        Notification.RelatedType.FOSTER_REQUEST
                ));
            }

            if (request.getFostererId() != null) {
                boolean fostererReminded = notificationRepository.existsByUserIdAndTypeAndRelatedIdAndRelatedType(
                        request.getFostererId(),
                        Notification.Type.FOSTER_RETURN_REMINDER,
                        request.getId(),
                        Notification.RelatedType.FOSTER_REQUEST
                );
                if (!fostererReminded) {
                    entries.add(NotificationEvent.entry(
                            request.getFostererId(),
                            Notification.Type.FOSTER_RETURN_REMINDER,
                            title, content,
                            request.getId(),
                            Notification.RelatedType.FOSTER_REQUEST
                    ));
                }
            }

            if (!entries.isEmpty()) {
                notificationPublisher.publish(entries);
                reminderCount += entries.size();
                log.info("寄养归还提醒已发送: requestId={}, 提前天数={}, 接收人数={}",
                        request.getId(), daysBefore, entries.size());
            }
        }

        return reminderCount;
    }
}
