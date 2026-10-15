package com.pulseai.repository;

import java.util.List;
import java.util.UUID;

import com.pulseai.entity.UserTopicPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTopicPreferenceRepository extends JpaRepository<UserTopicPreference, UUID> {
    List<UserTopicPreference> findByUser_IdOrderByTopic_NameAsc(UUID userId);
    void deleteByUser_Id(UUID userId);
}
