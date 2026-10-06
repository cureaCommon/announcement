package com.curea.announcement.web;

import com.curea.announcement.domain.AudienceType;
import com.curea.announcement.domain.RevisionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record RevisionView(RevisionStatus status, String title, String summary, String body, LocalDate publishDate, LocalDate expireDate, LocalTime publishTime, LocalTime expireTime,
                           boolean pinRequested, AudienceType audienceType, List<String> audienceRoles,
                           List<AttachmentView> attachments, String rejectReason, Instant submittedAt, Instant updatedOn) {
}
