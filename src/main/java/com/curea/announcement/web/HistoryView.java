package com.curea.announcement.web;

import java.time.Instant;

public record HistoryView(String action, Long actorId, String actorName, String note, Instant createdOn) {
}
