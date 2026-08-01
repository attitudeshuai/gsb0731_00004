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

    /** 管理后台：单个寄养人在某个月份的绩效指标。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FostererMonthlyPerformance {
        private String month;              // yyyy-MM
        private Long fostererId;
        private String fostererUsername;
        private long completedCount;       // 该月完成的寄养单数
        private long totalCount;           // 该月承接的寄养单数（不限状态）
        private double completionRate;     // 完成率（百分比，0-100，保留两位）
        private double averageRating;      // 该月收到评价的平均分（保留两位）
    }
}
