package com.curea.announcement.web;

import com.curea.announcement.domain.AnnouncementStatus;
import com.curea.announcement.domain.AudienceType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ManageAnnouncementView(Long id, AnnouncementStatus status, boolean scheduled, boolean expired,
                                     String title, String summary, String body, LocalDate publishDate, LocalDate expireDate, LocalTime publishTime, LocalTime expireTime,
                                     boolean pinned, boolean pinRequested, AudienceType audienceType,
                                     List<String> audienceRoles, List<AttachmentView> attachments,
                                     Long createdBy, Instant createdOn, Instant updatedOn, Instant submittedAt,
                                     Instant reviewedAt, String rejectReason, RevisionView revision) {
}
