package com.taskmanagement.service.notification;

import java.util.List;

import org.springframework.stereotype.Service;

import com.taskmanagement.dto.notification.NotificationResponse;
import com.taskmanagement.repository.NotificationRepository;
import com.taskmanagement.dto.Response;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public Response<List<NotificationResponse>> getAllNotification(Long recipientId, String message) {
        // Logic to send notification to the recipient
        // This could involve saving the notification to the database
        // and possibly sending it through other channels (e.g., email, push notification)
        return null;
    }

    public NotificationResponse getNotification(Long id, Long recipientId) {
        // Implementation for retrieving a notification
        return null;
    }

    public Response<List<NotificationResponse>> getAllNotification() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAllNotification'");
    }

    public Response<Long> getUnreadCount(Long userId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getUnreadCount'");
    }

    public Response<NotificationResponse> markAsRead(Long userId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'markAsRead'");
    }

    public Response<NotificationResponse> markAsReadAll(Long userId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'markAsReadAll'");
    }
}
