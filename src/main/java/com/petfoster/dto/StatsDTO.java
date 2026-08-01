package com.petfoster.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public class StatsDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverviewStats {
        private long totalUsers;
        private long totalPets;
        private long totalRequests;
        private long totalCompletedRequests;
        private long totalReviews;
        private long todayNewRequests;
        private Map<String, Long> requestStatusCount;
        private Map<String, Long> petSpeciesCount;
        private List<TopUser> topRatedUsers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopUser {
        private Long userId;
        private String username;
        private Double averageRating;
        private Double averageResponsibility;
        private Double averageCommunication;
        private Double averagePetCondition;
        private long reviewCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendStats {
        private String startDate;
        private String endDate;
        private List<DailyCount> dailyRequests;
        private List<DailyCount> dailyCompletedRequests;
        private List<DailyCount> dailyUsers;
        private List<DailyCount> dailyReviews;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCount {
        private String date;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetBreedRank {
        private String breed;
        private String species;
        private long requestCount;
        private double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PopularBreedStats {
        private List<PetBreedRank> topBreeds;
        private long totalRequests;
        private int totalBreeds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FosterDurationStats {
        private double averageDays;
        private double medianDays;
        private long shortestDays;
        private long longestDays;
        private long totalCompletedRequests;
        private Map<String, Double> averageBySpecies;
        private Map<String, Double> averageByBreed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserFosterStats {
        private long publishedRequests;
        private long completedFosters;
        private long receivedReviews;
        private double averageRating;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyFostererStats {
        private Long fostererId;
        private String fostererUsername;
        private String month;
        private long completedCount;
        private long totalCount;
        private long reviewCount;
        private double averageRating;
        private double completionRate;
    }
}
