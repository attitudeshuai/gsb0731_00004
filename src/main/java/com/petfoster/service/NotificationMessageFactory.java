package com.petfoster.service;

import com.petfoster.entity.FosterRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class NotificationMessageFactory {

    public String fosterRequestStatusText(FosterRequest.Status status) {
        return switch (status) {
            case Pending -> "待确认";
            case Approved -> "已同意";
            case InProgress -> "进行中";
            case Completed -> "已完成";
            case Cancelled -> "已取消";
        };
    }

    public String fosterRequestCreatedTitle() {
        return "收到新的寄养申请";
    }

    public String fosterRequestCreatedContent(String ownerName, String petName,
                                               LocalDate startDate, LocalDate endDate) {
        return String.format("%s 邀请您帮忙寄养宠物「%s」，寄养时间：%s 至 %s",
                ownerName, petName, startDate, endDate);
    }

    public String statusChangedTitle(FosterRequest.Status newStatus) {
        return String.format("寄养申请状态变更：%s", fosterRequestStatusText(newStatus));
    }

    public String statusChangedContent(String operatorName, String petName, FosterRequest.Status newStatus) {
        return String.format("%s 将宠物「%s」的寄养申请状态更新为「%s」",
                operatorName, petName, fosterRequestStatusText(newStatus));
    }

    public String requestTimeoutTitle() {
        return "寄养申请超时自动取消";
    }

    public String requestTimeoutContent(String petName, int timeoutDays, LocalDate startDate) {
        return String.format(
                "宠物「%s」的寄养申请因批准后超过 %d 天未开始，已自动取消（寄养开始日期：%s）",
                petName, timeoutDays, startDate);
    }

    public String returnReminderTitle(int daysBefore) {
        return String.format("寄养归还提醒：还有 %d 天", daysBefore);
    }

    public String returnReminderContent(String petName, LocalDate endDate) {
        return String.format(
                "宠物「%s」的寄养将于 %s 到期，请记得按时归还宠物，感谢您的配合！",
                petName, endDate);
    }

    public String dailyLogCreatedOwnerTitle() {
        return "新的寄养日报已发布";
    }

    public String dailyLogCreatedOwnerContent(String fostererName, LocalDate logDate) {
        return String.format("%s 发布了 %s 的寄养日报，请及时查看。", fostererName, logDate);
    }

    public String dailyLogCreatedSelfTitle() {
        return "寄养日报已发布";
    }

    public String dailyLogCreatedSelfContent(LocalDate logDate) {
        return String.format("您已发布 %s 的寄养日报，请持续记录宠物每日状况。", logDate);
    }

    public String dailyLogUpdatedTitle() {
        return "寄养日报已更新";
    }

    public String dailyLogUpdatedOwnerContent(String fostererName, LocalDate logDate) {
        return String.format("%s 更新了 %s 的寄养日报，请及时查看。", fostererName, logDate);
    }

    public String dailyLogUpdatedSelfContent(LocalDate logDate) {
        return String.format("您已更新 %s 的寄养日报，请继续关注宠物状况并及时记录。", logDate);
    }

    public String dailyLogReminderTitle() {
        return "今日寄养日报提醒";
    }

    public String dailyLogReminderContent(String petName, LocalDate today) {
        return String.format(
                "请记得为宠物「%s」填写今天（%s）的寄养日报，让主人了解宠物的状况。",
                petName, today);
    }

    public String missedDailyLogReminderTitle() {
        return "连续未填写日报提醒";
    }

    public String missedDailyLogReminderContent(int missedDays, String petName) {
        return String.format(
                "您已连续 %d 天未为宠物「%s」填写寄养日报，请尽快补填，避免主人担心。",
                missedDays, petName);
    }
}
