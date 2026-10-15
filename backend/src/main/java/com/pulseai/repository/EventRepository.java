package com.pulseai.repository;

import java.util.UUID;

import com.pulseai.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, UUID> {
}
