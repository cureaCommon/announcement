package com.curea.announcement.web;

import com.curea.announcement.domain.AudienceType;

import java.time.LocalDate;
import java.util.List;

public record PublicAnnouncementDetail(Long id, String title, String summary, String body, LocalDate publishDate, LocalDate expireDate,
                                       boolean pinned, AudienceType audienceType, List<String> audienceRoles,
                                       List<AttachmentView> attachments, boolean expired) {
}
