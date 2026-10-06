package com.curea.announcement.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record PublicAnnouncementDetail(Long id, String title, String summary, String body, LocalDate publishDate, LocalDate expireDate, LocalTime publishTime, LocalTime expireTime,
                                       boolean pinned,
                                       List<AttachmentView> attachments, boolean expired) {
}
