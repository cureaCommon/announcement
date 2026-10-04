package com.curea.announcement.web;

import java.time.Instant;

public record HistoryView(String action, Long actorId, String note, Instant createdOn) {
}
