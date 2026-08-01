package com.petfoster.service;

import com.petfoster.common.DateBucketUtils;
import com.petfoster.common.EntityLoader;
import com.petfoster.dto.StatsDTO;
import com.petfoster.entity.FosterRequest;
import com.petfoster.entity.Pet;
import com.petfoster.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    private final EntityLoader entityLoader;

    public StatsDTO.OverviewStats getOverviewStats() {
        long totalUsers = userRepository.count();
        long totalPets = petRepository.count();
        long totalRequests = requestRepository.count();
        long totalCompletedRequests = requestRepository.countByStatus(FosterRequest.Status.Completed);
        long totalReviews = reviewRepository.count();

        LocalDate today = LocalDate.now();
        long todayNewRequests = requestRepository.countByCreatedAtBetween(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        Map<String, Long> requestStatusCount = Arrays.stream(FosterRequest.Status.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        requestRepository::countByStatus
                ));

        Map<String, Long> petSpeciesCount = petRepository.countBySpecies().stream()
                .collect(Collectors.toMap(
                        PetRepository.SpeciesCount::getSpecies,
                        PetRepository.SpeciesCount::getCnt,
                        Long::sum
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
        LocalDate[] range = DateBucketUtils.normalizeRange(startDate, endDate);
        LocalDate start = range[0];
        LocalDate end = range[1];

        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.plusDays(1).atStartOfDay();

        List<StatsDTO.DailyCount> dailyRequests = DateBucketUtils.fillDailyCounts(
                start, end, requestRepository.countDailyByCreatedAtBetween(startDt, endDt));

        List<StatsDTO.DailyCount> dailyCompletedRequests = DateBucketUtils.fillDailyCounts(
                start, end, requestRepository.countDailyCompletedByCreatedAtBetween(startDt, endDt));

        List<StatsDTO.DailyCount> dailyUsers = DateBucketUtils.fillDailyCounts(
                start, end, userRepository.countDailyByCreatedAtBetween(startDt, endDt));

        List<StatsDTO.DailyCount> dailyReviews = DateBucketUtils.fillDailyCounts(
                start, end, reviewRepository.countDailyByCreatedAtBetween(startDt, endDt));

        return StatsDTO.TrendStats.builder()
                .startDate(start.toString())
                .endDate(end.toString())
                .dailyRequests(dailyRequests)
                .dailyCompletedRequests(dailyCompletedRequests)
                .dailyUsers(dailyUsers)
                .dailyReviews(dailyReviews)
                .build();
    }

    private List<StatsDTO.TopUser> calculateTopRatedUsers(int limit) {
        List<FosterReviewRepository.ReviewAggregation> aggregations =
                reviewRepository.aggregateAllByReviewee();

        Map<Long, String> usernameMap = userRepository.findAllById(
                aggregations.stream().map(FosterReviewRepository.ReviewAggregation::getUserId).toList()
        ).stream().collect(Collectors.toMap(
                com.petfoster.entity.User::getId,
                com.petfoster.entity.User::getUsername
        ));

        return aggregations.stream()
                .filter(a -> a.getReviewCount() > 0)
                .map(a -> StatsDTO.TopUser.builder()
                        .userId(a.getUserId())
                        .username(usernameMap.getOrDefault(a.getUserId(), "未知用户"))
                        .averageRating(round2(a.getAvgRating()))
                        .averageResponsibility(round2(a.getAvgResponsibility()))
                        .averageCommunication(round2(a.getAvgCommunication()))
                        .averagePetCondition(round2(a.getAvgPetCondition()))
                        .reviewCount(a.getReviewCount())
                        .build())
                .sorted((u1, u2) -> {
                    int cmp = Double.compare(u2.getAverageRating(), u1.getAverageRating());
                    if (cmp != 0) return cmp;
                    return Long.compare(u2.getReviewCount(), u1.getReviewCount());
                })
                .limit(limit)
                .toList();
    }

    private double round2(Double val) {
        if (val == null) return 0.0;
        return Math.round(val * 100.0) / 100.0;
    }

    public StatsDTO.PopularBreedStats getPopularBreedStats(int topN) {
        List<PetRepository.BreedRequestCount> breedRows = petRepository.countRequestsByBreed();

        long totalRequests = breedRows.stream()
                .mapToLong(PetRepository.BreedRequestCount::getCnt)
                .sum();

        List<StatsDTO.PetBreedRank> breedRanks = breedRows.stream()
                .map(row -> {
                    String breed = row.getBreed();
                    if (breed == null || breed.isEmpty()) {
                        breed = "未知品种";
                    }
                    String species = row.getSpecies() != null ? row.getSpecies() : "";
                    long count = row.getCnt();
                    double percentage = totalRequests > 0
                            ? Math.round(count * 10000.0 / totalRequests) / 100.0 : 0.0;

                    return StatsDTO.PetBreedRank.builder()
                            .breed(breed)
                            .species(species)
                            .requestCount(count)
                            .percentage(percentage)
                            .build();
                })
                .sorted((b1, b2) -> {
                    int cmp = Long.compare(b2.getRequestCount(), b1.getRequestCount());
                    if (cmp != 0) return cmp;
                    return b1.getBreed().compareTo(b2.getBreed());
                })
                .toList();

        if (topN > 0 && breedRanks.size() > topN) {
            breedRanks = breedRanks.subList(0, topN);
        }

        return StatsDTO.PopularBreedStats.builder()
                .topBreeds(breedRanks)
                .totalRequests(totalRequests)
                .totalBreeds(breedRows.size())
                .build();
    }

    public StatsDTO.FosterDurationStats getFosterDurationStats() {
        List<FosterRequest> completedRequests = requestRepository.findAllCompleted();

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

        Set<Long> petIds = completedRequests.stream()
                .map(FosterRequest::getPetId)
                .collect(Collectors.toSet());
        Map<Long, Pet> petMap = petRepository.findAllById(petIds).stream()
                .collect(Collectors.toMap(Pet::getId, p -> p));

        List<Long> durations = new ArrayList<>();
        Map<String, List<Long>> durationsBySpecies = new HashMap<>();
        Map<String, List<Long>> durationsByBreed = new HashMap<>();

        for (FosterRequest req : completedRequests) {
            if (req.getStartDate() == null || req.getEndDate() == null) continue;

            long days = ChronoUnit.DAYS.between(req.getStartDate(), req.getEndDate()) + 1;
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

        double averageDays = round2(durations.stream().mapToLong(Long::longValue).average().orElse(0.0));

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

        Map<String, Double> averageBySpecies = averageByGroup(durationsBySpecies);
        Map<String, Double> averageByBreed = averageByGroup(durationsByBreed);

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

    private Map<String, Double> averageByGroup(Map<String, List<Long>> grouped) {
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : grouped.entrySet()) {
            double avg = entry.getValue().stream().mapToLong(Long::longValue).average().orElse(0.0);
            result.put(entry.getKey(), Math.round(avg * 100.0) / 100.0);
        }
        return result;
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

    public List<StatsDTO.FostererMonthlyStats> getFostererMonthlyStats(
            LocalDate startDate, LocalDate endDate) {
        LocalDate[] range = DateBucketUtils.normalizeRange(startDate, endDate);
        LocalDate start = range[0];
        LocalDate end = range[1];
        LocalDate endExclusive = end.plusDays(1);

        List<FosterRequestRepository.FostererMonthlyCount> requestRows =
                requestRepository.aggregateMonthlyByFosterer(start, endExclusive);
        List<FosterReviewRepository.MonthlyReviewAggregation> reviewRows =
                reviewRepository.aggregateMonthlyByReviewee(start.atStartOfDay(), endExclusive.atStartOfDay());

        Map<String, FosterReviewRepository.MonthlyReviewAggregation> reviewMap = reviewRows.stream()
                .collect(Collectors.toMap(
                        r -> reviewKey(r.getUserId(), r.getMonth()),
                        r -> r,
                        (a, b) -> a
                ));

        Set<Long> fostererIds = requestRows.stream()
                .map(FosterRequestRepository.FostererMonthlyCount::getFostererId)
                .collect(Collectors.toSet());
        fostererIds.addAll(reviewRows.stream()
                .map(FosterReviewRepository.MonthlyReviewAggregation::getUserId)
                .collect(Collectors.toSet()));
        Map<Long, String> usernameMap = entityLoader.loadUserMap(fostererIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getUsername()));

        record Key(String month, Long fostererId) {}
        Map<Key, StatsDTO.FostererMonthlyStats> statsMap = new HashMap<>();

        for (FosterRequestRepository.FostererMonthlyCount row : requestRows) {
            long completed = row.getCompletedCount() != null ? row.getCompletedCount() : 0L;
            long cancelled = row.getCancelledCount() != null ? row.getCancelledCount() : 0L;
            long total = completed + cancelled;
            double rate = total > 0 ? Math.round(completed * 10000.0 / total) / 100.0 : 0.0;

            Key key = new Key(row.getMonth(), row.getFostererId());
            FosterReviewRepository.MonthlyReviewAggregation rev = reviewMap.get(
                    reviewKey(row.getFostererId(), row.getMonth()));
            double avgRating = rev != null && rev.getAvgRating() != null
                    ? Math.round(rev.getAvgRating() * 100.0) / 100.0 : 0.0;
            long reviewCount = rev != null && rev.getReviewCount() != null ? rev.getReviewCount() : 0L;

            statsMap.put(key, StatsDTO.FostererMonthlyStats.builder()
                    .month(row.getMonth())
                    .fostererId(row.getFostererId())
                    .fostererUsername(usernameMap.getOrDefault(row.getFostererId(), "未知用户"))
                    .completedCount(completed)
                    .cancelledCount(cancelled)
                    .completionRate(rate)
                    .averageRating(avgRating)
                    .reviewCount(reviewCount)
                    .build());
        }

        for (FosterReviewRepository.MonthlyReviewAggregation rev : reviewRows) {
            Key key = new Key(rev.getMonth(), rev.getUserId());
            if (statsMap.containsKey(key)) {
                continue;
            }
            double avgRating = rev.getAvgRating() != null
                    ? Math.round(rev.getAvgRating() * 100.0) / 100.0 : 0.0;
            long reviewCount = rev.getReviewCount() != null ? rev.getReviewCount() : 0L;

            statsMap.put(key, StatsDTO.FostererMonthlyStats.builder()
                    .month(rev.getMonth())
                    .fostererId(rev.getUserId())
                    .fostererUsername(usernameMap.getOrDefault(rev.getUserId(), "未知用户"))
                    .completedCount(0)
                    .cancelledCount(0)
                    .completionRate(0.0)
                    .averageRating(avgRating)
                    .reviewCount(reviewCount)
                    .build());
        }

        return statsMap.values().stream()
                .sorted(Comparator.comparing(StatsDTO.FostererMonthlyStats::getMonth).reversed()
                        .thenComparing(StatsDTO.FostererMonthlyStats::getCompletedCount, Comparator.reverseOrder())
                        .thenComparing(StatsDTO.FostererMonthlyStats::getAverageRating, Comparator.reverseOrder()))
                .toList();
    }

    private String reviewKey(Long userId, String month) {
        return userId + ":" + month;
    }
}
