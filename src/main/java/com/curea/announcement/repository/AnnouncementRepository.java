package com.curea.announcement.repository;

import com.curea.announcement.domain.Announcement;
import com.curea.announcement.domain.AnnouncementStatus;
import com.curea.announcement.domain.RevisionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long>, JpaSpecificationExecutor<Announcement> {

    Optional<Announcement> findByIdAndDeletedFalse(Long id);

    @Query("select count(a) from Announcement a left join a.revision r where a.deleted = false and (a.status = :status or r.status = :revisionStatus)")
    long countAwaitingReview(@Param("status") AnnouncementStatus status, @Param("revisionStatus") RevisionStatus revisionStatus);
}
