package com.petfoster.service;

import com.petfoster.common.DateBuckets;
import com.petfoster.dto.StatsDTO;
import com.petfoster.entity.FosterRequest;
import com.petfoster.entity.Pet;
import com.petfoster.entity.User;
import com.petfoster.repository.*;
import com.petfoster.repository.projection.BreedCount;
import com.petfoster.repository.projection.DateCount;
import com.petfoster.repository.projection.MonthlyFostererCount;
import com.petfoster.repository.projection.MonthlyFostererRating;
import com.petfoster.repository.projection.RatingAggregate;
import com.petfoster.repository.projection.SpeciesCount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
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

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        long todayNewRequests = requestRepository.countByCreatedAtBetween(todayStart, tomorrowStart);

        Map<String, Long> requestStatusCount = Arrays.stream(FosterRequest.Status.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        status -> requestRepository.countByStatus(status)
                ));

        Map<String, Long> petSpeciesCount = petRepository.countBySpecies().stream()
                .collect(Collectors.toMap(
                        sc -> sc.getSpecies() != null ? sc.getSpecies() : "未知",
                        SpeciesCount::getCount,
                        (a, b) -> a
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

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        Map<LocalDate, Long> requestCounts = toDateMap(
                requestRepository.countDailyByCreatedAtBetween(start, end));
        Map<LocalDate, Long> completedCounts = toDateMap(
                requestRepository.countCompletedDailyByCreatedAtBetween(start, end));
        Map<LocalDate, Long> userCounts = toDateMap(
                userRepository.countDailyByCreatedAtBetween(start, end));
        Map<LocalDate, Long> reviewCounts = toDateMap(
                reviewRepository.countDailyByCreatedAtBetween(start, end));

        return StatsDTO.TrendStats.builder()
                .startDate(startDate.format(DATE_FORMATTER))
                .endDate(endDate.format(DATE_FORMATTER))
                .dailyRequests(toDailyCounts(startDate, endDate, requestCounts))
                .dailyCompletedRequests(toDailyCounts(startDate, endDate, completedCounts))
                .dailyUsers(toDailyCounts(startDate, endDate, userCounts))
                .dailyReviews(toDailyCounts(startDate, endDate, reviewCounts))
                .build();
    }

    private Map<LocalDate, Long> toDateMap(List<DateCount> dateCounts) {
        return dateCounts.stream()
                .collect(Collectors.toMap(DateCount::getDate, DateCount::getCount, (a, b) -> a));
    }

    private List<StatsDTO.DailyCount> toDailyCounts(
            LocalDate start, LocalDate end, Map<LocalDate, Long> countsByDate) {
        return DateBuckets.fill(start, end, countsByDate,
                (date, count) -> StatsDTO.DailyCount.builder()
                        .date(date.format(DATE_FORMATTER))
                        .count(count)
                        .build());
    }

    private List<StatsDTO.TopUser> calculateTopRatedUsers(int limit) {
        List<RatingAggregate> aggregates = reviewRepository.findRatingAggregates();
        if (aggregates.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = aggregates.stream()
                .map(RatingAggregate::getRevieweeId)
                .toList();
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<StatsDTO.TopUser> allUsers = aggregates.stream()
                .map(agg -> {
                    User user = userMap.get(agg.getRevieweeId());
                    String username = user != null ? user.getUsername() : "未知用户";
                    double avgRating = round2(agg.getAvgRating() != null ? agg.getAvgRating() : 0.0);
                    double avgResp = round2(agg.getAvgResponsibility() != null ? agg.getAvgResponsibility() : 0.0);
                    double avgComm = round2(agg.getAvgCommunication() != null ? agg.getAvgCommunication() : 0.0);
                    double avgPet = round2(agg.getAvgPetCondition() != null ? agg.getAvgPetCondition() : 0.0);

                    return StatsDTO.TopUser.builder()
                            .userId(agg.getRevieweeId())
                            .username(username)
                            .averageRating(avgRating)
                            .averageResponsibility(avgResp)
                            .averageCommunication(avgComm)
                            .averagePetCondition(avgPet)
                            .reviewCount(agg.getReviewCount() != null ? agg.getReviewCount() : 0L)
                            .build();
                })
                .toList();

        return allUsers.stream()
                .sorted(Comparator.comparingDouble(StatsDTO.TopUser::getAverageRating).reversed()
                        .thenComparing(Comparator.comparingLong(StatsDTO.TopUser::getReviewCount).reversed()))
                .limit(limit)
                .toList();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public StatsDTO.PopularBreedStats getPopularBreedStats(int topN) {
        List<BreedCount> breedCounts = requestRepository.countByBreed();

        Map<String, long[]> merged = new LinkedHashMap<>();
        Map<String, String> speciesByBreed = new HashMap<>();
        for (BreedCount bc : breedCounts) {
            String breed = bc.getBreed();
            if (breed == null || breed.isEmpty()) {
                breed = "未知品种";
            }
            long count = bc.getCount() != null ? bc.getCount() : 0L;

            merged.computeIfAbsent(breed, k -> new long[]{0})[0] += count;
            speciesByBreed.putIfAbsent(breed, bc.getSpecies() != null ? bc.getSpecies() : "");
        }

        long totalRequests = merged.values().stream().mapToLong(arr -> arr[0]).sum();

        List<StatsDTO.PetBreedRank> breedRanks = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : merged.entrySet()) {
            String breed = entry.getKey();
            long count = entry.getValue()[0];
            String species = speciesByBreed.getOrDefault(breed, "");

            double percentage = totalRequests > 0 ? Math.round(count * 10000.0 / totalRequests) / 100.0 : 0.0;

            breedRanks.add(StatsDTO.PetBreedRank.builder()
                    .breed(breed)
                    .species(species)
                    .requestCount(count)
                    .percentage(percentage)
                    .build());
        }

        breedRanks.sort(Comparator.comparingLong(StatsDTO.PetBreedRank::getRequestCount).reversed()
                .thenComparing(StatsDTO.PetBreedRank::getBreed));

        if (topN > 0 && breedRanks.size() > topN) {
            breedRanks = breedRanks.subList(0, topN);
        }

        return StatsDTO.PopularBreedStats.builder()
                .topBreeds(breedRanks)
                .totalRequests(totalRequests)
                .totalBreeds(merged.size())
                .build();
    }

    public StatsDTO.FosterDurationStats getFosterDurationStats() {
        List<FosterRequest> completedRequests = requestRepository.findCompletedWithPet();

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

        List<Long> durations = new ArrayList<>();
        Map<String, List<Long>> durationsBySpecies = new HashMap<>();
        Map<String, List<Long>> durationsByBreed = new HashMap<>();

        for (FosterRequest req : completedRequests) {
            if (req.getStartDate() == null || req.getEndDate() == null) {
                continue;
            }

            long days = ChronoUnit.DAYS.between(req.getStartDate(), req.getEndDate()) + 1;
            durations.add(days);

            Pet pet = req.getPet();
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
        medianDays = round2(medianDays);

        long shortestDays = durations.get(0);
        long longestDays = durations.get(durations.size() - 1);

        Map<String, Double> averageBySpecies = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : durationsBySpecies.entrySet()) {
            double avg = entry.getValue().stream().mapToLong(Long::longValue).average().orElse(0.0);
            averageBySpecies.put(entry.getKey(), round2(avg));
        }

        Map<String, Double> averageByBreed = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : durationsByBreed.entrySet()) {
            double avg = entry.getValue().stream().mapToLong(Long::longValue).average().orElse(0.0);
            averageByBreed.put(entry.getKey(), round2(avg));
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
        double averageRating = avgRating != null ? round2(avgRating) : 0.0;

        return StatsDTO.UserFosterStats.builder()
                .publishedRequests(publishedRequests)
                .completedFosters(completedFosters)
                .receivedReviews(receivedReviews)
                .averageRating(averageRating)
                .build();
    }

    public List<StatsDTO.MonthlyFostererStats> getMonthlyFostererStats(
            LocalDate startDate, LocalDate endDate, Long fostererId) {
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusMonths(12).withDayOfMonth(1);
        }
        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }

        LocalDateTime start = startDate.withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = endDate.plusMonths(1).withDayOfMonth(1).atStartOfDay();

        List<MonthlyFostererCount> counts = requestRepository.countMonthlyByFosterer(start, end);
        List<MonthlyFostererRating> ratings = reviewRepository.findMonthlyFostererRatings(start, end);

        Map<String, MonthlyFostererCount> countByKey = counts.stream()
                .filter(c -> fostererId == null || fostererId.equals(c.getFostererId()))
                .collect(Collectors.toMap(
                        c -> monthlyKey(c.getFostererId(), c.getMonth()),
                        Function.identity(),
                        (a, b) -> a));
        Map<String, MonthlyFostererRating> ratingByKey = ratings.stream()
                .filter(r -> fostererId == null || fostererId.equals(r.getFostererId()))
                .collect(Collectors.toMap(
                        r -> monthlyKey(r.getFostererId(), r.getMonth()),
                        Function.identity(),
                        (a, b) -> a));

        Set<Long> fostererIds = new HashSet<>();
        countByKey.values().forEach(c -> fostererIds.add(c.getFostererId()));
        ratingByKey.values().forEach(r -> fostererIds.add(r.getFostererId()));
        Map<Long, User> userMap = fostererIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(fostererIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(countByKey.keySet());
        allKeys.addAll(ratingByKey.keySet());

        return allKeys.stream()
                .map(key -> {
                    MonthlyFostererCount c = countByKey.get(key);
                    MonthlyFostererRating r = ratingByKey.get(key);

                    Long fid = c != null ? c.getFostererId() : r.getFostererId();
                    String month = c != null ? c.getMonth() : r.getMonth();
                    long completed = c != null && c.getCompletedCount() != null ? c.getCompletedCount() : 0L;
                    long total = c != null && c.getTotalCount() != null ? c.getTotalCount() : 0L;
                    long reviewCount = r != null && r.getReviewCount() != null ? r.getReviewCount() : 0L;
                    double avgRating = r != null && r.getAvgRating() != null ? round2(r.getAvgRating()) : 0.0;
                    double rate = total > 0 ? round2(completed * 100.0 / total) : 0.0;

                    User fosterer = userMap.get(fid);
                    String username = fosterer != null ? fosterer.getUsername() : "未知用户";

                    return StatsDTO.MonthlyFostererStats.builder()
                            .fostererId(fid)
                            .fostererUsername(username)
                            .month(month)
                            .completedCount(completed)
                            .totalCount(total)
                            .reviewCount(reviewCount)
                            .averageRating(avgRating)
                            .completionRate(rate)
                            .build();
                })
                .sorted(Comparator.comparing(StatsDTO.MonthlyFostererStats::getMonth).reversed()
                        .thenComparing(Comparator.comparingDouble(StatsDTO.MonthlyFostererStats::getCompletionRate).reversed())
                        .thenComparing(Comparator.comparingLong(StatsDTO.MonthlyFostererStats::getCompletedCount).reversed()))
                .toList();
    }

    private String monthlyKey(Long fostererId, String month) {
        return fostererId + "|" + month;
    }
}
