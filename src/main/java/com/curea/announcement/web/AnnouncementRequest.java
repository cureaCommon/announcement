package com.curea.announcement.web;

import com.curea.announcement.domain.AudienceType;

import java.time.LocalDate;
import java.util.List;

public record AnnouncementRequest(String title, String summary, String body, LocalDate publishDate, LocalDate expireDate,
                                  Boolean pinRequested, AudienceType audienceType, List<String> audienceRoles,
                                  List<String> attachmentIds) {
}
