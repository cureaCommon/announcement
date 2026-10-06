package com.curea.announcement.web;

import com.curea.announcement.domain.AudienceType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AnnouncementRequest(String title, String summary, String body, LocalDate publishDate, LocalDate expireDate, LocalTime publishTime, LocalTime expireTime,
                                  Boolean pinRequested, AudienceType audienceType, List<String> audienceRoles,
                                  List<String> attachmentIds) {
}
