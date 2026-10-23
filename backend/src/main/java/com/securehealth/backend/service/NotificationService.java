package com.securehealth.backend.service;

import com.securehealth.backend.exception.NotFoundException;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.Notification;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Creates and reads in-app notifications. Messages must never contain clinical details. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private LoginRepository loginRepository;

    /** Joins the caller's transaction (so a rolled-back operation leaves no notification) and never throws. */
    @Transactional
    public void notify(Login recipient, String type, String title, String message) {
        if (recipient == null || recipient.getUserId() == null) {
            return;
        }
        try {
            Notification n = new Notification();
            n.setUser(recipient);
            n.setType(type);
            n.setTitle(title);
            n.setMessage(message);
            n.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(n);
        } catch (RuntimeException e) {
            log.warn("Could not store notification of type {}", type, e);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> mine(String email) {
        Login user = user(email);
        List<Map<String, Object>> items = notificationRepository
                .findTop50ByUser_UserIdOrderByCreatedAtDesc(user.getUserId()).stream()
                .map(NotificationService::view)
                .collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unread", notificationRepository.countByUser_UserIdAndReadFalse(user.getUserId()));
        body.put("items", items);
        return body;
    }

    @Transactional
    public void markRead(String email, Long id) {
        Login user = user(email);
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!n.getUser().getUserId().equals(user.getUserId())) {
            throw new NotFoundException("Notification not found"); // do not reveal other users' ids
        }
        n.setRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public int markAllRead(String email) {
        return notificationRepository.markAllRead(user(email).getUserId());
    }

    private Login user(String email) {
        return loginRepository.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private static Map<String, Object> view(Notification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("type", n.getType());
        m.put("title", n.getTitle());
        m.put("message", n.getMessage());
        m.put("createdAt", n.getCreatedAt());
        m.put("read", n.isRead());
        return m;
    }
}
