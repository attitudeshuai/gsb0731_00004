package com.petfoster.service;

import com.petfoster.dto.StatsDTO;
import com.petfoster.entity.User;
import com.petfoster.repository.FosterDailyLogRepository;
import com.petfoster.repository.FosterRequestRepository;
import com.petfoster.repository.FosterReviewRepository;
import com.petfoster.repository.PetRepository;
import com.petfoster.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PetRepository petRepository;

    @Mock
    private FosterRequestRepository requestRepository;

    @Mock
    private FosterReviewRepository reviewRepository;

    @Mock
    private FosterDailyLogRepository dailyLogRepository;

    @InjectMocks
    private StatsService statsService;

    @Test
    @DisplayName("寄养人月度统计 - 正常组装完成数、平均评分和完成率")
    void testGetFostererMonthlyStats_Success() {
        YearMonth month = YearMonth.of(2026, 8);
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        when(requestRepository.countGroupByFosterer(monthStart, monthEnd))
                .thenReturn(List.of(
                        new Object[]{1L, 3L, 4L},
                        new Object[]{2L, 0L, 2L}
                ));
        when(reviewRepository.averageRatingGroupByReviewee(
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 4.567}));
        when(userRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(
                        User.builder().id(1L).username("fosterer1").build(),
                        User.builder().id(2L).username("fosterer2").build()
                ));

        StatsDTO.FostererMonthlyStats result = statsService.getFostererMonthlyStats(month);

        assertEquals("2026-08", result.getMonth());
        assertEquals(2, result.getFosterers().size());

        StatsDTO.FostererPerformance first = result.getFosterers().get(0);
        assertEquals(1L, first.getFostererId());
        assertEquals("fosterer1", first.getFostererUsername());
        assertEquals(3, first.getCompletedCount());
        assertEquals(4, first.getTotalCount());
        assertEquals(75.0, first.getCompletionRate());
        assertEquals(4.57, first.getAverageRating());

        StatsDTO.FostererPerformance second = result.getFosterers().get(1);
        assertEquals(2L, second.getFostererId());
        assertEquals("fosterer2", second.getFostererUsername());
        assertEquals(0, second.getCompletedCount());
        assertEquals(2, second.getTotalCount());
        assertEquals(0.0, second.getCompletionRate());
        assertEquals(0.0, second.getAverageRating());
    }

    @Test
    @DisplayName("寄养人月度统计 - 不传月份默认当月，无数据时返回空列表")
    void testGetFostererMonthlyStats_DefaultMonthAndEmpty() {
        when(requestRepository.countGroupByFosterer(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(reviewRepository.averageRatingGroupByReviewee(
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(userRepository.findAllById(List.of())).thenReturn(List.of());

        StatsDTO.FostererMonthlyStats result = statsService.getFostererMonthlyStats(null);

        assertEquals(YearMonth.now().toString(), result.getMonth());
        assertNotNull(result.getFosterers());
        assertTrue(result.getFosterers().isEmpty());
    }
}
