package com.petfoster.service;

import com.petfoster.common.BusinessException;
import com.petfoster.common.PageResponse;
import com.petfoster.common.PageSort;
import com.petfoster.dto.DailyLogDTO;
import com.petfoster.entity.FosterDailyLog;
import com.petfoster.entity.FosterRequest;
import com.petfoster.entity.Notification;
import com.petfoster.entity.Pet;
import com.petfoster.entity.User;
import com.petfoster.repository.FosterDailyLogRepository;
import com.petfoster.repository.FosterRequestRepository;
import com.petfoster.repository.PetRepository;
import com.petfoster.repository.UserRepository;
import com.petfoster.service.support.NotificationHelper;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyLogService {

    private static final String DEFAULT_SORT_FIELD = "logDate";
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "logDate", "logDate",
            "log_date", "logDate"
    );

    private final FosterDailyLogRepository logRepository;
    private final FosterRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final NotificationHelper notificationHelper;
    private final FileStorageService fileStorageService;

    public PageResponse<DailyLogDTO.LogResponse> getLogs(
            int page, int size, String sort,
            Long requestId, Long fostererId, LocalDate startDate, LocalDate endDate) {

        var pageable = PageSort.of(page, size, sort, DEFAULT_SORT_FIELD, SORT_FIELDS);

        Page<FosterDailyLog> logPage = logRepository.searchLogs(
                requestId, fostererId, startDate, endDate, pageable);

        return buildPageResponse(logPage);
    }

    public DailyLogDTO.LogResponse getLogById(Long id) {
        FosterDailyLog log = logRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("寄养日报不存在"));
        User fosterer = userRepository.findById(log.getFostererId()).orElse(null);
        return EntityMapper.toDailyLogResponse(log, fosterer);
    }

    @Transactional
    public DailyLogDTO.LogResponse createLog(Long userId, DailyLogDTO.CreateLogRequest req) {
        return createLog(userId, req, null);
    }

    @Transactional
    public DailyLogDTO.LogResponse createLog(Long userId, DailyLogDTO.CreateLogRequest req, org.springframework.web.multipart.MultipartFile[] photoFiles) {
        FosterRequest request = requestRepository.findById(req.getRequestId())
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));

        if (request.getStatus() != FosterRequest.Status.InProgress
                && request.getStatus() != FosterRequest.Status.Approved) {
            throw BusinessException.badRequest("只有已批准或进行中的寄养申请才能创建日报");
        }

        boolean isFosterer = request.getFostererId() != null
                && request.getFostererId().equals(userId);
        if (!isFosterer) {
            throw BusinessException.forbidden("只有寄养人才能创建日报");
        }

        if (req.getLogDate() == null) {
            throw BusinessException.badRequest("日志日期不能为空");
        }

        if (req.getLogDate().isBefore(request.getStartDate())
                || req.getLogDate().isAfter(request.getEndDate())) {
            throw BusinessException.badRequest("日志日期必须在寄养期间内");
        }

        if (logRepository.existsByRequestIdAndLogDate(req.getRequestId(), req.getLogDate())) {
            throw BusinessException.badRequest("该日期的日报已存在，请使用更新接口");
        }

        List<String> uploadedPhotoUrls = new ArrayList<>();
        List<String> photoUrlList = new ArrayList<>();

        try {
            if (StringUtils.hasText(req.getPhotos())) {
                photoUrlList.addAll(List.of(req.getPhotos().split(",")));
            }

            if (photoFiles != null && photoFiles.length > 0) {
                for (org.springframework.web.multipart.MultipartFile photoFile : photoFiles) {
                    if (photoFile != null && !photoFile.isEmpty()) {
                        String photoUrl = fileStorageService.uploadFile(photoFile);
                        photoUrlList.add(photoUrl);
                        uploadedPhotoUrls.add(photoUrl);
                    }
                }
                log.info("日报照片上传完成: 共上传 {} 张照片", uploadedPhotoUrls.size());
            }

            String photos = photoUrlList.isEmpty() ? null : String.join(",", photoUrlList);

            FosterDailyLog dailyLog = FosterDailyLog.builder()
                    .requestId(req.getRequestId())
                    .fostererId(userId)
                    .logDate(req.getLogDate())
                    .food(req.getFood())
                    .mood(req.getMood())
                    .photos(photos)
                    .note(req.getNote())
                    .build();

            dailyLog = logRepository.save(dailyLog);
            log.info("寄养日报创建成功: logId={}, requestId={}, photoCount={}",
                    dailyLog.getId(), req.getRequestId(), photoUrlList.size());

            User fosterer = userRepository.findById(userId).orElse(null);
            String fostererName = EntityMapper.usernameOr(fosterer, "寄养人");

            notificationHelper.builder()
                    .add(request.getOwnerId(),
                            Notification.Type.DAILY_LOG_CREATED,
                            "新的寄养日报已发布",
                            String.format("%s 发布了 %s 的寄养日报，请及时查看。",
                                    fostererName, req.getLogDate()),
                            dailyLog.getId(),
                            Notification.RelatedType.DAILY_LOG)
                    .add(userId,
                            Notification.Type.DAILY_LOG_CREATED,
                            "寄养日报已发布",
                            String.format("您已发布 %s 的寄养日报，请持续记录宠物每日状况。",
                                    req.getLogDate()),
                            dailyLog.getId(),
                            Notification.RelatedType.DAILY_LOG)
                    .publish();

            return EntityMapper.toDailyLogResponse(dailyLog, fosterer);
        } catch (Exception e) {
            if (!uploadedPhotoUrls.isEmpty()) {
                for (String photoUrl : uploadedPhotoUrls) {
                    fileStorageService.deleteFile(photoUrl);
                }
                log.warn("数据库操作失败，已清理 {} 个孤儿文件", uploadedPhotoUrls.size());
            }
            throw e;
        }
    }

    @Transactional
    public DailyLogDTO.LogResponse updateLog(Long userId, Long logId, DailyLogDTO.UpdateLogRequest req) {
        return updateLog(userId, logId, req, null, false);
    }

    @Transactional
    public DailyLogDTO.LogResponse updateLog(Long userId, Long logId, DailyLogDTO.UpdateLogRequest req,
            org.springframework.web.multipart.MultipartFile[] photoFiles, boolean replacePhotos) {
        FosterDailyLog dailyLog = logRepository.findById(logId)
                .orElseThrow(() -> BusinessException.notFound("寄养日报不存在"));

        if (!dailyLog.getFostererId().equals(userId)) {
            throw BusinessException.forbidden("无权限修改此日报");
        }

        String oldPhotos = dailyLog.getPhotos();
        List<String> uploadedPhotoUrls = new ArrayList<>();

        try {
            if (req.getLogDate() != null) {
                FosterRequest request = requestRepository.findById(dailyLog.getRequestId())
                        .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));
                if (req.getLogDate().isBefore(request.getStartDate())
                        || req.getLogDate().isAfter(request.getEndDate())) {
                    throw BusinessException.badRequest("日志日期必须在寄养期间内");
                }
                if (!req.getLogDate().equals(dailyLog.getLogDate())
                        && logRepository.existsByRequestIdAndLogDate(dailyLog.getRequestId(), req.getLogDate())) {
                    throw BusinessException.badRequest("该日期的日报已存在");
                }
                dailyLog.setLogDate(req.getLogDate());
            }

            if (req.getFood() != null) {
                dailyLog.setFood(req.getFood());
            }
            if (req.getMood() != null) {
                dailyLog.setMood(req.getMood());
            }
            if (req.getNote() != null) {
                dailyLog.setNote(req.getNote());
            }

            List<String> photoUrlList = new ArrayList<>();

            if (!replacePhotos && StringUtils.hasText(oldPhotos)) {
                photoUrlList.addAll(List.of(oldPhotos.split(",")));
            }

            if (req.getPhotos() != null) {
                if (replacePhotos || !StringUtils.hasText(oldPhotos)) {
                    if (StringUtils.hasText(req.getPhotos())) {
                        photoUrlList.addAll(List.of(req.getPhotos().split(",")));
                    }
                } else {
                    if (StringUtils.hasText(req.getPhotos())) {
                        photoUrlList.addAll(List.of(req.getPhotos().split(",")));
                    }
                }
            }

            if (photoFiles != null && photoFiles.length > 0) {
                for (org.springframework.web.multipart.MultipartFile photoFile : photoFiles) {
                    if (photoFile != null && !photoFile.isEmpty()) {
                        String photoUrl = fileStorageService.uploadFile(photoFile);
                        photoUrlList.add(photoUrl);
                        uploadedPhotoUrls.add(photoUrl);
                    }
                }
                log.info("日报照片上传完成: 共上传 {} 张新照片", uploadedPhotoUrls.size());
            }

            String newPhotos = photoUrlList.isEmpty() ? null : String.join(",", photoUrlList);
            dailyLog.setPhotos(newPhotos);

            dailyLog = logRepository.save(dailyLog);
            log.info("寄养日报更新成功: logId={}, photoCount={}", logId, photoUrlList.size());

            if (replacePhotos && StringUtils.hasText(oldPhotos)) {
                String oldPhotosToDelete = oldPhotos;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (String oldPhotoUrl : oldPhotosToDelete.split(",")) {
                            if (StringUtils.hasText(oldPhotoUrl) && oldPhotoUrl.startsWith("/uploads/")) {
                                fileStorageService.deleteFile(oldPhotoUrl.trim());
                            }
                        }
                        log.info("旧日报照片已清理(事务提交后): logId={}, count={}", logId, oldPhotosToDelete.split(",").length);
                    }
                });
            }

            FosterRequest request = requestRepository.findById(dailyLog.getRequestId()).orElse(null);
            User fosterer = userRepository.findById(dailyLog.getFostererId()).orElse(null);
            String fostererName = EntityMapper.usernameOr(fosterer, "寄养人");

            if (request != null) {
                notificationHelper.builder()
                        .add(request.getOwnerId(),
                                Notification.Type.DAILY_LOG_UPDATED,
                                "寄养日报已更新",
                                String.format("%s 更新了 %s 的寄养日报，请及时查看。",
                                        fostererName, dailyLog.getLogDate()),
                                dailyLog.getId(),
                                Notification.RelatedType.DAILY_LOG)
                        .add(dailyLog.getFostererId(),
                                Notification.Type.DAILY_LOG_UPDATED,
                                "寄养日报已更新",
                                String.format("您已更新 %s 的寄养日报，请继续关注宠物状况并及时记录。",
                                        dailyLog.getLogDate()),
                                dailyLog.getId(),
                                Notification.RelatedType.DAILY_LOG)
                        .publish();
            }

            return EntityMapper.toDailyLogResponse(dailyLog, fosterer);
        } catch (Exception e) {
            if (!uploadedPhotoUrls.isEmpty()) {
                for (String photoUrl : uploadedPhotoUrls) {
                    fileStorageService.deleteFile(photoUrl);
                }
                log.warn("数据库操作失败，已清理 {} 个孤儿文件", uploadedPhotoUrls.size());
            }
            throw e;
        }
    }

    @Transactional
    public void deleteLog(Long userId, Long logId) {
        FosterDailyLog dailyLog = logRepository.findById(logId)
                .orElseThrow(() -> BusinessException.notFound("寄养日报不存在"));

        if (!dailyLog.getFostererId().equals(userId)) {
            throw BusinessException.forbidden("无权限删除此日报");
        }

        String photos = dailyLog.getPhotos();

        logRepository.delete(dailyLog);
        log.info("寄养日报删除成功: logId={}, userId={}", logId, userId);

        if (StringUtils.hasText(photos)) {
            for (String photoUrl : photos.split(",")) {
                if (StringUtils.hasText(photoUrl) && photoUrl.startsWith("/uploads/")) {
                    fileStorageService.deleteFile(photoUrl.trim());
                }
            }
            log.info("日报照片已清理: logId={}", logId);
        }
    }

    private PageResponse<DailyLogDTO.LogResponse> buildPageResponse(Page<FosterDailyLog> page) {
        List<Long> fostererIds = page.getContent().stream()
                .map(FosterDailyLog::getFostererId).distinct().toList();
        Map<Long, User> userMap = EntityCollections.toIdMap(
                userRepository.findAllById(fostererIds), User::getId);

        return PageResponse.from(page,
                l -> EntityMapper.toDailyLogResponse(l, userMap.get(l.getFostererId())));
    }

    public int sendDailyLogReminders() {
        LocalDate today = LocalDate.now();
        List<FosterRequest> inProgressRequests = requestRepository.findInProgressOnDate(today);

        if (inProgressRequests.isEmpty()) {
            log.info("今日无进行中的寄养申请，跳过日报提醒");
            return 0;
        }

        NotificationHelper.Builder builder = notificationHelper.builder();
        int reminderCount = 0;

        for (FosterRequest request : inProgressRequests) {
            if (request.getFostererId() == null) {
                continue;
            }

            boolean hasTodayLog = logRepository.existsByRequestIdAndLogDate(request.getId(), today);

            if (!hasTodayLog) {
                String petName = EntityMapper.petNameOr(
                        petRepository.findById(request.getPetId()).orElse(null), "宠物");

                String content = String.format(
                        "请记得为宠物「%s」填写今天（%s）的寄养日报，让主人了解宠物的状况。",
                        petName, today);

                builder.add(request.getFostererId(),
                        Notification.Type.DAILY_LOG_REMINDER,
                        "今日寄养日报提醒",
                        content,
                        request.getId(),
                        Notification.RelatedType.FOSTER_REQUEST);
                reminderCount++;
                log.info("已生成日报提醒: requestId={}, fostererId={}, petName={}",
                        request.getId(), request.getFostererId(), petName);
            }
        }

        if (reminderCount > 0) {
            builder.publish();
            log.info("日报提醒发送完成，共发送 {} 条提醒", reminderCount);
        }

        return reminderCount;
    }

    public int sendMissedDailyLogReminders(int missedDays) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(missedDays - 1);
        List<FosterRequest> inProgressRequests = requestRepository.findInProgressOnDate(today);

        if (inProgressRequests.isEmpty()) {
            log.info("无进行中的寄养申请，跳过连续未写日报提醒");
            return 0;
        }

        NotificationHelper.Builder builder = notificationHelper.builder();
        int reminderCount = 0;

        for (FosterRequest request : inProgressRequests) {
            if (request.getFostererId() == null) {
                continue;
            }

            LocalDate effectiveStartDate = request.getStartDate().isAfter(startDate)
                    ? request.getStartDate() : startDate;

            if (effectiveStartDate.isAfter(today)) {
                continue;
            }

            long daysInRange = today.toEpochDay() - effectiveStartDate.toEpochDay() + 1;
            long logCount = logRepository.countByRequestIdAndLogDateBetween(
                    request.getId(), effectiveStartDate, today);

            if (logCount == 0 && daysInRange >= missedDays) {
                String petName = EntityMapper.petNameOr(
                        petRepository.findById(request.getPetId()).orElse(null), "宠物");

                String content = String.format(
                        "您已连续 %d 天未为宠物「%s」填写寄养日报，请尽快补填，避免主人担心。",
                        missedDays, petName);

                builder.add(request.getFostererId(),
                        Notification.Type.DAILY_LOG_MISSED_REMINDER,
                        "连续未填写日报提醒",
                        content,
                        request.getId(),
                        Notification.RelatedType.FOSTER_REQUEST);
                reminderCount++;
                log.info("已生成连续未写日报提醒: requestId={}, fostererId={}, petName={}, 连续{}天未写",
                        request.getId(), request.getFostererId(), petName, missedDays);
            }
        }

        if (reminderCount > 0) {
            builder.publish();
            log.info("连续未写日报提醒发送完成，共发送 {} 条提醒", reminderCount);
        }

        return reminderCount;
    }
}
