package com.petfoster.service;

import com.petfoster.common.DateBuckets;
import com.petfoster.dto.StatsDTO;
import com.petfoster.entity.FosterRequest;
import com.petfoster.entity.Pet;
import com.petfoster.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final FosterRequestRepository requestRepository;
    private final FosterReviewRepository reviewRepository;
    private final FosterDailyLogRepository dailyLogRepository;
    private final LookupService lookupService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    public StatsDTO.OverviewStats getOverviewStats() {
        long totalUsers = userRepository.count();
        long totalPets = petRepository.count();
        long totalRequests = requestRepository.count();
        long totalCompletedRequests = requestRepository.countByStatus(FosterRequest.Status.Completed);
        long totalReviews = reviewRepository.count();

        LocalDate today = LocalDate.now();
        long todayNewRequests = requestRepository.countCreatedBetween(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        Map<String, Long> requestStatusCount = new LinkedHashMap<>();
        for (FosterRequest.Status status : FosterRequest.Status.values()) {
            requestStatusCount.put(status.name(), 0L);
        }
        for (Object[] row : requestRepository.countGroupByStatus()) {
            requestStatusCount.put(((FosterRequest.Status) row[0]).name(), ((Number) row[1]).longValue());
        }

        Map<String, Long> petSpeciesCount = new LinkedHashMap<>();
        for (Object[] row : petRepository.countGroupBySpecies()) {
            petSpeciesCount.put((String) row[0], ((Number) row[1]).longValue());
        }

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

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        List<StatsDTO.DailyCount> dailyRequests = toDailySeries(
                startDate, endDate, requestRepository.countDailyCreatedBetween(start, end));
        List<StatsDTO.DailyCount> dailyCompletedRequests = toDailySeries(
                startDate, endDate, requestRepository.countDailyCompletedBetween(start, end));
        List<StatsDTO.DailyCount> dailyUsers = toDailySeries(
                startDate, endDate, userRepository.countDailyCreatedBetween(start, end));
        List<StatsDTO.DailyCount> dailyReviews = toDailySeries(
                startDate, endDate, reviewRepository.countDailyCreatedBetween(start, end));

        return StatsDTO.TrendStats.builder()
                .startDate(startDate.format(DATE_FORMATTER))
                .endDate(endDate.format(DATE_FORMATTER))
                .dailyRequests(dailyRequests)
                .dailyCompletedRequests(dailyCompletedRequests)
                .dailyUsers(dailyUsers)
                .dailyReviews(dailyReviews)
                .build();
    }

    /** 把仓储层按日聚合的原始结果，零填充为覆盖整个区间的每日计数序列。 */
    private List<StatsDTO.DailyCount> toDailySeries(LocalDate startDate, LocalDate endDate, List<Object[]> rows) {
        Map<LocalDate, Long> counts = DateBuckets.toCountMap(rows);
        return DateBuckets.series(startDate, endDate, counts,
                (date, count) -> StatsDTO.DailyCount.builder()
                        .date(date.format(DATE_FORMATTER))
                        .count(count)
                        .build());
    }

    private List<StatsDTO.TopUser> calculateTopRatedUsers(int limit) {
        return reviewRepository.aggregateTopRatedUsers().stream()
                .map(row -> StatsDTO.TopUser.builder()
                        .userId(((Number) row[0]).longValue())
                        .username((String) row[1])
                        .averageRating(roundToTwo(row[2]))
                        .averageResponsibility(roundToTwo(row[3]))
                        .averageCommunication(roundToTwo(row[4]))
                        .averagePetCondition(roundToTwo(row[5]))
                        .reviewCount(((Number) row[6]).longValue())
                        .build())
                .limit(limit)
                .toList();
    }

    private double roundToTwo(Object avg) {
        double value = avg != null ? ((Number) avg).doubleValue() : 0.0;
        return Math.round(value * 100.0) / 100.0;
    }

    public StatsDTO.PopularBreedStats getPopularBreedStats(int topN) {
        List<Object[]> rows = requestRepository.countRequestsByBreedAndSpecies();

        // 同一品种可能对应不同种类（数据不规整），按品种合并计数并取一个代表种类。
        Map<String, Long> countByBreed = new LinkedHashMap<>();
        Map<String, String> speciesByBreed = new LinkedHashMap<>();
        long totalRequests = 0;

        for (Object[] row : rows) {
            String breed = normalizeBreed((String) row[0]);
            String species = (String) row[1];
            long count = ((Number) row[2]).longValue();

            countByBreed.merge(breed, count, Long::sum);
            speciesByBreed.putIfAbsent(breed, species != null ? species : "");
            totalRequests += count;
        }

        long total = totalRequests;
        List<StatsDTO.PetBreedRank> breedRanks = new ArrayList<>();
        for (Map.Entry<String, Long> entry : countByBreed.entrySet()) {
            long count = entry.getValue();
            double percentage = total > 0 ? Math.round(count * 10000.0 / total) / 100.0 : 0.0;
            breedRanks.add(StatsDTO.PetBreedRank.builder()
                    .breed(entry.getKey())
                    .species(speciesByBreed.getOrDefault(entry.getKey(), ""))
                    .requestCount(count)
                    .percentage(percentage)
                    .build());
        }

        breedRanks.sort((b1, b2) -> {
            int countCompare = Long.compare(b2.getRequestCount(), b1.getRequestCount());
            if (countCompare != 0) return countCompare;
            return b1.getBreed().compareTo(b2.getBreed());
        });

        if (topN > 0 && breedRanks.size() > topN) {
            breedRanks = breedRanks.subList(0, topN);
        }

        return StatsDTO.PopularBreedStats.builder()
                .topBreeds(breedRanks)
                .totalRequests(totalRequests)
                .totalBreeds(countByBreed.size())
                .build();
    }

    private String normalizeBreed(String breed) {
        return (breed == null || breed.isEmpty()) ? "未知品种" : breed;
    }

    public StatsDTO.FosterDurationStats getFosterDurationStats() {
        List<FosterRequest> completedRequests = requestRepository.findAll().stream()
                .filter(r -> r.getStatus() == FosterRequest.Status.Completed)
                .toList();

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
        for (Pet pet : petRepository.findAll()) {
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

    /**
     * 管理后台：按月份统计每个寄养人的完成单数、平均评分与完成率。
     *
     * <p>沿用第 2 轮的聚合下推风格——单数/完成数与月度平均分都在仓储层用 GROUP BY 算好，
     * 服务层只做「按 [月份, 寄养人] 合并 + 计算完成率 + 补全寄养人用户名」。
     * 用户名沿用第 1 轮的 {@code LookupService} 兜底，避免逐条查库写空值判断。
     * 月份归属以寄养申请的结束日期为准，与评价的月度口径保持一致。
     */
    public List<StatsDTO.FostererMonthlyPerformance> getFostererMonthlyPerformance(
            LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            startDate = LocalDate.now().withDayOfMonth(1).minusMonths(5);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }

        // key: "yyyy-MM|fostererId"
        Map<String, StatsDTO.FostererMonthlyPerformance.FostererMonthlyPerformanceBuilder> byMonthAndFosterer =
                new LinkedHashMap<>();

        for (Object[] row : requestRepository.aggregateFostererMonthlyCounts(startDate, endDate)) {
            String month = formatMonth(row[0], row[1]);
            Long fostererId = ((Number) row[2]).longValue();
            long completedCount = ((Number) row[3]).longValue();
            long totalCount = ((Number) row[4]).longValue();
            double completionRate = totalCount > 0
                    ? Math.round(completedCount * 10000.0 / totalCount) / 100.0 : 0.0;

            byMonthAndFosterer.put(month + "|" + fostererId,
                    StatsDTO.FostererMonthlyPerformance.builder()
                            .month(month)
                            .fostererId(fostererId)
                            .completedCount(completedCount)
                            .totalCount(totalCount)
                            .completionRate(completionRate)
                            .averageRating(0.0));
        }

        for (Object[] row : reviewRepository.aggregateFostererMonthlyRatings(startDate, endDate)) {
            String month = formatMonth(row[0], row[1]);
            Long fostererId = ((Number) row[2]).longValue();
            var builder = byMonthAndFosterer.get(month + "|" + fostererId);
            if (builder != null) {
                builder.averageRating(roundToTwo(row[3]));
            }
        }

        return byMonthAndFosterer.values().stream()
                .map(StatsDTO.FostererMonthlyPerformance.FostererMonthlyPerformanceBuilder::build)
                .peek(p -> p.setFostererUsername(lookupService.usernameOr(p.getFostererId(), "未知用户")))
                .sorted(Comparator
                        .comparing(StatsDTO.FostererMonthlyPerformance::getMonth)
                        .thenComparing(StatsDTO.FostererMonthlyPerformance::getCompletedCount, Comparator.reverseOrder())
                        .thenComparing(StatsDTO.FostererMonthlyPerformance::getFostererId))
                .toList();
    }

    private String formatMonth(Object year, Object month) {
        int y = ((Number) year).intValue();
        int m = ((Number) month).intValue();
        return LocalDate.of(y, m, 1).format(MONTH_FORMATTER);
    }
}
