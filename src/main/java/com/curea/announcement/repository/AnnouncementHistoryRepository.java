package com.curea.announcement.repository;

import com.curea.announcement.domain.AnnouncementHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementHistoryRepository extends JpaRepository<AnnouncementHistory, Long> {

    List<AnnouncementHistory> findByAnnouncementIdOrderByCreatedOnAsc(Long announcementId);
}
