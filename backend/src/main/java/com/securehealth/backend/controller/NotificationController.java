package com.securehealth.backend.controller;

import com.securehealth.backend.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** The signed-in user's own notifications. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public ResponseEntity<?> mine(Authentication auth) {
        return ResponseEntity.ok(notificationService.mine(auth.getName()));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, Authentication auth) {
        notificationService.markRead(auth.getName(), id);
        return ResponseEntity.ok(Map.of("message", "Notification marked as read."));
    }

    @PutMapping("/read-all")
    public ResponseEntity<?> markAllRead(Authentication auth) {
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllRead(auth.getName())));
    }
}
