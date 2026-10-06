package com.curea.announcement.service;

import com.curea.announcement.client.DocumentClient;
import com.curea.announcement.domain.Announcement;
import com.curea.announcement.domain.AnnouncementHistory;
import com.curea.announcement.domain.AnnouncementStatus;
import com.curea.announcement.domain.Attachment;
import com.curea.announcement.repository.AnnouncementHistoryRepository;
import com.curea.announcement.repository.AnnouncementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class AnnouncementMaintenance {

    private static final Logger logger = LoggerFactory.getLogger(AnnouncementMaintenance.class);
    private static final long SYSTEM_ACTOR = 0L;

    private final AnnouncementRepository repository;
    private final AnnouncementHistoryRepository historyRepository;
    private final DocumentClient documentClient;
    private final TransactionTemplate transactionTemplate;
    private final Duration expiredRetention;
    private final Duration documentGrace;

    public AnnouncementMaintenance(AnnouncementRepository repository,
                                   AnnouncementHistoryRepository historyRepository,
                                   DocumentClient documentClient,
                                   TransactionTemplate transactionTemplate,
                                   @Value("${announcement.expired-retention-days:30}") long expiredRetentionDays,
                                   @Value("${announcement.document-grace-minutes:60}") long documentGraceMinutes) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.documentClient = documentClient;
        this.transactionTemplate = transactionTemplate;
        this.expiredRetention = Duration.ofDays(expiredRetentionDays);
        this.documentGrace = Duration.ofMinutes(documentGraceMinutes);
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "${announcement.maintenance-initial-delay:PT10M}")
    public void run() {
        try {
            deleteExpired();
        } catch (RuntimeException ex) {
            logger.error("Deleting expired announcements failed", ex);
        }
        try {
            removeUnusedDocuments();
        } catch (RuntimeException ex) {
            logger.error("Removing unused announcement documents failed", ex);
        }
    }

    public int deleteExpired() {
        Instant cutoff = Instant.now().minus(expiredRetention);
        Integer deleted = transactionTemplate.execute(status -> {
            List<Announcement> expired = repository
                    .findTop100ByDeletedFalseAndStatusAndExpireAtBeforeOrderByIdAsc(AnnouncementStatus.PUBLISHED, cutoff);
            Instant now = Instant.now();
            for (Announcement announcement : expired) {
                announcement.setDeleted(true);
                announcement.setPinned(false);
                announcement.setUpdatedOn(now);
                historyRepository.save(new AnnouncementHistory(announcement.getId(), "EXPIRED_DELETED", SYSTEM_ACTOR, null));
            }
            return expired.size();
        });
        int count = deleted == null ? 0 : deleted;
        if (count > 0) {
            logger.info("Deleted {} announcements expired for more than {} days", count, expiredRetention.toDays());
        }
        return count;
    }

    public int removeUnusedDocuments() {
        List<DocumentClient.AttachedDocument> attached = documentClient.attachedSyncedBefore(Instant.now().minus(documentGrace));
        if (attached.isEmpty()) {
            return 0;
        }
        Map<String, List<String>> byOwner = new LinkedHashMap<>();
        for (DocumentClient.AttachedDocument document : attached) {
            byOwner.computeIfAbsent(document.ownerId(), key -> new ArrayList<>()).add(document.id());
        }
        int removed = 0;
        for (Map.Entry<String, List<String>> entry : byOwner.entrySet()) {
            Set<String> used = usedDocumentIds(entry.getKey());
            List<String> unused = entry.getValue().stream().filter(id -> !used.contains(id)).toList();
            if (unused.isEmpty()) {
                continue;
            }
            try {
                documentClient.remove(entry.getKey(), unused);
                removed += unused.size();
            } catch (RuntimeException ex) {
                logger.error("Removing unused documents of announcement {} failed", entry.getKey(), ex);
            }
        }
        if (removed > 0) {
            logger.info("Removed {} documents no longer used by any announcement", removed);
        }
        return removed;
    }

    private Set<String> usedDocumentIds(String ownerId) {
        Long id = parseId(ownerId);
        if (id == null) {
            return Set.of();
        }
        Set<String> used = transactionTemplate.execute(status -> {
            Optional<Announcement> announcement = repository.findById(id);
            if (announcement.isEmpty() || announcement.get().isDeleted()) {
                return Set.<String>of();
            }
            Set<String> ids = new HashSet<>();
            announcement.get().getAttachments().stream().map(Attachment::getDocumentId).forEach(ids::add);
            if (announcement.get().getRevision() != null) {
                announcement.get().getRevision().getAttachments().stream().map(Attachment::getDocumentId).forEach(ids::add);
            }
            return ids;
        });
        return used == null ? Set.of() : used;
    }

    private static Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
