package com.pulseai.repository;

import java.util.UUID;

import com.pulseai.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, UUID> {
}
