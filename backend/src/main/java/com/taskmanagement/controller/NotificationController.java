package com.taskmanagement.controller;

import org.springframework.web.bind.annotation.RestController;

import com.taskmanagement.dto.Response;
import com.taskmanagement.dto.notification.NotificationResponse;
import com.taskmanagement.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController 
@RequestMapping("/api/notifications")
@RequiredArgsConstructor 
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping("/")
    public Response<List<NotificationResponse>> getAllNotification() {
        return notificationService.getAllNotification();
    }

    @GetMapping("/unread-count")
    public Response<Long> getUnreadCount(@RequestParam Long userId) {
        return notificationService.getUnreadCount(userId);
    }

    @PatchMapping("/mark-as-read")
    public Response<NotificationResponse> markAsRead(@RequestParam Long userId) {
        return notificationService.markAsRead(userId);
    }

    @PatchMapping("/mark-as-read-all")
    public Response<NotificationResponse> markAsReadAll(@RequestParam Long userId) {
        return notificationService.markAsReadAll(userId);
    }
}
