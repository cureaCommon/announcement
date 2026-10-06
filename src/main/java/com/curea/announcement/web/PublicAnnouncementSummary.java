package com.curea.announcement.web;

import java.time.LocalDate;

public record PublicAnnouncementSummary(Long id, String title, String excerpt, LocalDate publishDate, boolean pinned,
                                        int attachmentCount) {
}
