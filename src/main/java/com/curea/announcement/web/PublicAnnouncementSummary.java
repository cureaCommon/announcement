package com.curea.announcement.web;

import com.curea.announcement.domain.AudienceType;

import java.time.LocalDate;
import java.util.List;

public record PublicAnnouncementSummary(Long id, String title, String excerpt, LocalDate publishDate, boolean pinned,
                                        AudienceType audienceType, List<String> audienceRoles, int attachmentCount) {
}
