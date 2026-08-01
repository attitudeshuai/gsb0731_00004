package com.petfoster.service;

import com.petfoster.common.BusinessException;
import com.petfoster.common.PageResponse;
import com.petfoster.common.SortResolver;
import com.petfoster.dto.ReviewDTO;
import com.petfoster.entity.FosterRequest;
import com.petfoster.entity.FosterReview;
import com.petfoster.entity.User;
import com.petfoster.repository.FosterRequestRepository;
import com.petfoster.repository.FosterReviewRepository;
import com.petfoster.repository.UserRepository;
import com.petfoster.util.EntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final FosterReviewRepository reviewRepository;
    private final FosterRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final LookupService lookupService;

    private static final SortResolver SORT_RESOLVER = SortResolver.withDefault("createdAt")
            .allow("rating")
            .allow("createdAt")
            .alias("created_at", "createdAt")
            .build();

    private int calculateOverallRating(int responsibility, int communication, int petCondition) {
        double avg = (responsibility + communication + petCondition) / 3.0;
        return (int) Math.round(avg);
    }

    public PageResponse<ReviewDTO.ReviewResponse> getReviews(
            int page, int size, String sort,
            Long requestId, Long reviewerId, Long revieweeId,
            Integer minRating, Integer maxRating) {

        Sort sortObj = SORT_RESOLVER.resolve(sort);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Page<FosterReview> reviewPage = reviewRepository.searchReviews(
                requestId, reviewerId, revieweeId, minRating, maxRating, pageable);

        return buildPageResponse(reviewPage);
    }

    public ReviewDTO.ReviewResponse getReviewById(Long id) {
        FosterReview review = reviewRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("评价不存在"));
        return buildSingleResponse(review);
    }

    @Transactional
    public ReviewDTO.ReviewResponse createReview(Long userId, ReviewDTO.CreateReviewRequest req) {
        FosterRequest request = requestRepository.findById(req.getRequestId())
                .orElseThrow(() -> BusinessException.notFound("寄养申请不存在"));

        if (request.getStatus() != FosterRequest.Status.Completed) {
            throw BusinessException.badRequest("只能对已完成的寄养申请进行评价");
        }

        boolean isOwner = request.getOwnerId().equals(userId);
        boolean isFosterer = request.getFostererId() != null
                && request.getFostererId().equals(userId);
        if (!isOwner && !isFosterer) {
            throw BusinessException.forbidden("只有参与寄养的双方才能评价");
        }

        Long expectedRevieweeId = isOwner ? request.getFostererId() : request.getOwnerId();
        if (!req.getRevieweeId().equals(expectedRevieweeId)) {
            throw BusinessException.badRequest("被评价人ID不正确，应该评价对方用户");
        }

        if (reviewRepository.existsByRequestIdAndReviewerId(req.getRequestId(), userId)) {
            throw BusinessException.badRequest("您已对此寄养申请进行过评价");
        }

        int overallRating = calculateOverallRating(
                req.getResponsibilityRating(),
                req.getCommunicationRating(),
                req.getPetConditionRating()
        );

        FosterReview review = FosterReview.builder()
                .requestId(req.getRequestId())
                .reviewerId(userId)
                .revieweeId(req.getRevieweeId())
                .responsibilityRating(req.getResponsibilityRating())
                .communicationRating(req.getCommunicationRating())
                .petConditionRating(req.getPetConditionRating())
                .rating(overallRating)
                .content(req.getContent())
                .build();

        review = reviewRepository.save(review);
        log.info("评价创建成功: reviewId={}, requestId={}, reviewerId={}",
                review.getId(), req.getRequestId(), userId);

        return buildSingleResponse(review);
    }

    @Transactional
    public ReviewDTO.ReviewResponse updateReview(
            Long userId, Long reviewId, ReviewDTO.UpdateReviewRequest req) {

        FosterReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> BusinessException.notFound("评价不存在"));

        if (!review.getReviewerId().equals(userId)) {
            throw BusinessException.forbidden("无权限修改此评价");
        }

        boolean needRecalculate = false;

        if (req.getResponsibilityRating() != null) {
            review.setResponsibilityRating(req.getResponsibilityRating());
            needRecalculate = true;
        }
        if (req.getCommunicationRating() != null) {
            review.setCommunicationRating(req.getCommunicationRating());
            needRecalculate = true;
        }
        if (req.getPetConditionRating() != null) {
            review.setPetConditionRating(req.getPetConditionRating());
            needRecalculate = true;
        }
        if (needRecalculate) {
            int overallRating = calculateOverallRating(
                    review.getResponsibilityRating(),
                    review.getCommunicationRating(),
                    review.getPetConditionRating()
            );
            review.setRating(overallRating);
        }
        if (req.getContent() != null) {
            review.setContent(req.getContent());
        }

        review = reviewRepository.save(review);
        log.info("评价更新成功: reviewId={}", reviewId);

        return buildSingleResponse(review);
    }

    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        FosterReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> BusinessException.notFound("评价不存在"));

        if (!review.getReviewerId().equals(userId)) {
            throw BusinessException.forbidden("无权限删除此评价");
        }

        reviewRepository.delete(review);
        log.info("评价删除成功: reviewId={}, userId={}", reviewId, userId);
    }

    private PageResponse<ReviewDTO.ReviewResponse> buildPageResponse(Page<FosterReview> page) {
        Set<Long> userIds = page.getContent().stream()
                .flatMap(r -> Stream.of(r.getReviewerId(), r.getRevieweeId()))
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return PageResponse.from(page, r -> EntityMapper.toReviewResponse(
                r,
                userMap.get(r.getReviewerId()),
                userMap.get(r.getRevieweeId())
        ));
    }

    private ReviewDTO.ReviewResponse buildSingleResponse(FosterReview r) {
        User reviewer = lookupService.userOrNull(r.getReviewerId());
        User reviewee = lookupService.userOrNull(r.getRevieweeId());
        return EntityMapper.toReviewResponse(r, reviewer, reviewee);
    }
}
