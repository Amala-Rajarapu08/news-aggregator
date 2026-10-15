package com.pulseai.repository;

import java.util.UUID;

import com.pulseai.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}
