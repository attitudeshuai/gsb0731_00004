package com.petfoster.service;

import com.petfoster.dto.StatsDTO;
import com.petfoster.entity.FosterRequest;
import com.petfoster.entity.Pet;
import com.petfoster.entity.User;
import com.petfoster.repository.*;
import com.petfoster.util.DateBucketUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final FosterRequestRepository requestRepository;
    private final FosterReviewRepository reviewRepository;
    private final FosterDailyLogRepository dailyLogRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public StatsDTO.OverviewStats getOverviewStats() {
        long totalUsers = userRepository.count();
        long totalPets = petRepository.count();
        long totalRequests = requestRepository.count();
        long totalCompletedRequests = requestRepository.countByStatus(FosterRequest.Status.Completed);
        long totalReviews = reviewRepository.count();

        LocalDate today = LocalDate.now();
        long todayNewRequests = requestRepository.countCreatedBetween(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        Map<String, Long> requestStatusCount = Arrays.stream(FosterRequest.Status.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        status -> requestRepository.countByStatus(status)
                ));

        Map<String, Long> petSpeciesCount = petRepository.countGroupBySpecies().stream()
                .collect(Collectors.toMap(
                        row -> row[0] != null ? (String) row[0] : "未知",
                        row -> ((Number) row[1]).longValue()
                ));

        List<StatsDTO.TopUser> topRatedUsers = calculateTopRatedUsers(10);

        return StatsDTO.OverviewStats.builder()
                .totalUsers(totalUsers)
                .totalPets(totalPets)
                .totalRequests(totalRequests)
                .totalCompletedRequests(totalCompletedRequests)
                .totalReviews(totalReviews)
                .todayNewRequests(todayNewRequests)
                .requestStatusCount(requestStatusCount)
                .petSpeciesCount(petSpeciesCount)
                .topRatedUsers(topRatedUsers)
                .build();
    }

    public StatsDTO.TrendStats getTrendStats(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }

        List<LocalDate> dateRange = DateBucketUtils.range(startDate, endDate);
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.plusDays(1).atStartOfDay();

        Map<LocalDate, Long> dailyRequestsMap = DateBucketUtils.toCountMap(
                requestRepository.countGroupByDate(from, to));
        Map<LocalDate, Long> dailyCompletedMap = DateBucketUtils.toCountMap(
                requestRepository.countByStatusGroupByDate(FosterRequest.Status.Completed, from, to));
        Map<LocalDate, Long> dailyUsersMap = DateBucketUtils.toCountMap(
                userRepository.countGroupByDate(from, to));
        Map<LocalDate, Long> dailyReviewsMap = DateBucketUtils.toCountMap(
                reviewRepository.countGroupByDate(from, to));

        return StatsDTO.TrendStats.builder()
                .startDate(startDate.format(DATE_FORMATTER))
                .endDate(endDate.format(DATE_FORMATTER))
                .dailyRequests(toDailyCounts(dateRange, dailyRequestsMap))
                .dailyCompletedRequests(toDailyCounts(dateRange, dailyCompletedMap))
                .dailyUsers(toDailyCounts(dateRange, dailyUsersMap))
                .dailyReviews(toDailyCounts(dateRange, dailyReviewsMap))
                .build();
    }

    private List<StatsDTO.DailyCount> toDailyCounts(List<LocalDate> dateRange, Map<LocalDate, Long> counts) {
        return DateBucketUtils.fillGaps(dateRange, counts,
                (day, count) -> StatsDTO.DailyCount.builder()
                        .date(day.format(DATE_FORMATTER))
                        .count(count)
                        .build());
    }

    private List<StatsDTO.TopUser> calculateTopRatedUsers(int limit) {
        List<Object[]> rows = reviewRepository.findTopRatedUsers(PageRequest.of(0, limit));
        List<Long> userIds = rows.stream().map(row -> (Long) row[0]).toList();
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return rows.stream()
                .map(row -> {
                    Long userId = (Long) row[0];
                    User user = userMap.get(userId);
                    return StatsDTO.TopUser.builder()
                            .userId(userId)
                            .username(user != null ? user.getUsername() : null)
                            .averageRating(round2((Double) row[1]))
                            .averageResponsibility(round2((Double) row[2]))
                            .averageCommunication(round2((Double) row[3]))
                            .averagePetCondition(round2((Double) row[4]))
                            .reviewCount(((Number) row[5]).longValue())
                            .build();
                })
                .toList();
    }

    private double round2(Double value) {
        return value != null ? Math.round(value * 100.0) / 100.0 : 0.0;
    }

    public StatsDTO.PopularBreedStats getPopularBreedStats(int topN) {
        List<Object[]> rows = requestRepository.countGroupByBreed();
        long totalRequests = rows.stream().mapToLong(row -> ((Number) row[2]).longValue()).sum();

        List<StatsDTO.PetBreedRank> breedRanks = rows.stream()
                .map(row -> {
                    long count = ((Number) row[2]).longValue();
                    double percentage = totalRequests > 0
                            ? Math.round(count * 10000.0 / totalRequests) / 100.0 : 0.0;
                    return StatsDTO.PetBreedRank.builder()
                            .breed((String) row[0])
                            .species((String) row[1])
                            .requestCount(count)
                            .percentage(percentage)
                            .build();
                })
                .toList();

        List<StatsDTO.PetBreedRank> topBreeds = topN > 0
                ? breedRanks.stream().limit(topN).toList()
                : breedRanks;

        return StatsDTO.PopularBreedStats.builder()
                .topBreeds(topBreeds)
                .totalRequests(totalRequests)
                .totalBreeds(breedRanks.size())
                .build();
    }

    public StatsDTO.FosterDurationStats getFosterDurationStats() {
        List<FosterRequest> completedRequests = requestRepository.findByStatus(FosterRequest.Status.Completed);

        if (completedRequests.isEmpty()) {
            return StatsDTO.FosterDurationStats.builder()
                    .averageDays(0)
                    .medianDays(0)
                    .shortestDays(0)
                    .longestDays(0)
                    .totalCompletedRequests(0)
                    .averageBySpecies(new HashMap<>())
                    .averageByBreed(new HashMap<>())
                    .build();
        }

        Map<Long, Pet> petMap = new HashMap<>();
        List<Long> petIds = completedRequests.stream()
                .map(FosterRequest::getPetId)
                .distinct()
                .toList();
        for (Pet pet : petRepository.findAllById(petIds)) {
            petMap.put(pet.getId(), pet);
        }

        List<Long> durations = new ArrayList<>();
        Map<String, List<Long>> durationsBySpecies = new HashMap<>();
        Map<String, List<Long>> durationsByBreed = new HashMap<>();

        for (FosterRequest req : completedRequests) {
            if (req.getStartDate() == null || req.getEndDate() == null) continue;

            long days = java.time.temporal.ChronoUnit.DAYS.between(req.getStartDate(), req.getEndDate()) + 1;
            durations.add(days);

            Pet pet = petMap.get(req.getPetId());
            if (pet != null) {
                String species = pet.getSpecies();
                if (species != null && !species.isEmpty()) {
                    durationsBySpecies.computeIfAbsent(species, k -> new ArrayList<>()).add(days);
                }

                String breed = pet.getBreed();
                if (breed == null || breed.isEmpty()) {
                    breed = "未知品种";
                }
                durationsByBreed.computeIfAbsent(breed, k -> new ArrayList<>()).add(days);
            }
        }

        Collections.sort(durations);

        double averageDays = durations.stream().mapToLong(Long::longValue).average().orElse(0.0);
        averageDays = Math.round(averageDays * 100.0) / 100.0;

        double medianDays;
        int size = durations.size();
        if (size % 2 == 0) {
            medianDays = (durations.get(size / 2 - 1) + durations.get(size / 2)) / 2.0;
        } else {
            medianDays = durations.get(size / 2);
        }
        medianDays = Math.round(medianDays * 100.0) / 100.0;

        long shortestDays = durations.get(0);
        long longestDays = durations.get(durations.size() - 1);

        Map<String, Double> averageBySpecies = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : durationsBySpecies.entrySet()) {
            double avg = entry.getValue().stream().mapToLong(Long::longValue).average().orElse(0.0);
            averageBySpecies.put(entry.getKey(), Math.round(avg * 100.0) / 100.0);
        }

        Map<String, Double> averageByBreed = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : durationsByBreed.entrySet()) {
            double avg = entry.getValue().stream().mapToLong(Long::longValue).average().orElse(0.0);
            averageByBreed.put(entry.getKey(), Math.round(avg * 100.0) / 100.0);
        }

        return StatsDTO.FosterDurationStats.builder()
                .averageDays(averageDays)
                .medianDays(medianDays)
                .shortestDays(shortestDays)
                .longestDays(longestDays)
                .totalCompletedRequests(completedRequests.size())
                .averageBySpecies(averageBySpecies)
                .averageByBreed(averageByBreed)
                .build();
    }

    /**
     * 寄养人月度表现：按寄养结束日期落在该月的申请分组到寄养人，
     * 完成率 = 已完成 / 当月全部已指派申请（百分制，保留两位小数），
     * 平均评分取该月内收到的评价均值；未收到评价时为 0.0。
     */
    public StatsDTO.FostererMonthlyStats getFostererMonthlyStats(YearMonth month) {
        if (month == null) {
            month = YearMonth.now();
        }
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        List<Object[]> rows = requestRepository.countGroupByFosterer(monthStart, monthEnd);

        Map<Long, Double> ratingMap = reviewRepository.averageRatingGroupByReviewee(
                monthStart.atStartOfDay(), monthEnd.plusDays(1).atStartOfDay()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Double) row[1]));

        List<Long> fostererIds = rows.stream().map(row -> (Long) row[0]).toList();
        Map<Long, User> userMap = userRepository.findAllById(fostererIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<StatsDTO.FostererPerformance> fosterers = rows.stream()
                .map(row -> {
                    Long fostererId = (Long) row[0];
                    long completedCount = ((Number) row[1]).longValue();
                    long totalCount = ((Number) row[2]).longValue();
                    User fosterer = userMap.get(fostererId);
                    return StatsDTO.FostererPerformance.builder()
                            .fostererId(fostererId)
                            .fostererUsername(fosterer != null ? fosterer.getUsername() : null)
                            .completedCount(completedCount)
                            .totalCount(totalCount)
                            .averageRating(round2(ratingMap.get(fostererId)))
                            .completionRate(totalCount > 0
                                    ? Math.round(completedCount * 10000.0 / totalCount) / 100.0 : 0.0)
                            .build();
                })
                .toList();

        return StatsDTO.FostererMonthlyStats.builder()
                .month(month.toString())
                .fosterers(fosterers)
                .build();
    }

    public StatsDTO.UserFosterStats getUserFosterStats(Long userId) {
        long publishedRequests = requestRepository.countByOwnerId(userId);
        long completedFosters = requestRepository.countCompletedByUserId(userId);
        long receivedReviews = reviewRepository.countByRevieweeId(userId);

        Double avgRating = reviewRepository.findAverageRatingByRevieweeId(userId);
        double averageRating = avgRating != null ? Math.round(avgRating * 100.0) / 100.0 : 0.0;

        return StatsDTO.UserFosterStats.builder()
                .publishedRequests(publishedRequests)
                .completedFosters(completedFosters)
                .receivedReviews(receivedReviews)
                .averageRating(averageRating)
                .build();
    }
}
