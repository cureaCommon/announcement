package com.curea.announcement.service;

import com.curea.announcement.client.DocumentClient;
import com.curea.announcement.config.CurrentUser;
import com.curea.announcement.domain.*;
import com.curea.announcement.repository.AnnouncementHistoryRepository;
import com.curea.announcement.repository.AnnouncementRepository;
import com.curea.announcement.service.AnnouncementSpecifications.Scope;
import com.curea.announcement.web.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnnouncementService {

    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final int MAX_TITLE = 200;
    private static final int MAX_SUMMARY = 250;
    private static final int MAX_IMAGES = 20;
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final int EXCERPT_LENGTH = 220;
    private static final int MAX_BODY = 100_000;
    private static final int MAX_ATTACHMENTS = 5;
    private static final int MAX_REASON = 1000;
    private static final int MAX_PAGE_SIZE = 50;

    private final AnnouncementRepository repository;
    private final AnnouncementHistoryRepository historyRepository;
    private final DocumentClient documentClient;
    private final ReviewNotifier notifier;
    private final Set<String> allowedAudienceRoles;

    public AnnouncementService(AnnouncementRepository repository, AnnouncementHistoryRepository historyRepository,
                               DocumentClient documentClient, ReviewNotifier notifier,
                               @Value("${announcement.audience-roles:ADMIN,TUSEB_TTO,ANNOUNCEMENT_EDITOR}") String audienceRoles) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.documentClient = documentClient;
        this.notifier = notifier;
        this.allowedAudienceRoles = Arrays.stream(audienceRoles.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicAnnouncementSummary> listVisible(CurrentUser user, Scope scope, String text, Integer year,
                                                               boolean pinnedOnly, int page, int size) {
        Instant now = Instant.now();
        Specification<Announcement> spec = Specification.allOf(
                AnnouncementSpecifications.visibleTo(user, scope, now),
                AnnouncementSpecifications.matchesText(text),
                AnnouncementSpecifications.publishedInYear(year, ZONE),
                AnnouncementSpecifications.pinnedOnly(pinnedOnly));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("publishAt"), Sort.Order.desc("id")));
        Page<Announcement> result = repository.findAll(spec, pageable);
        return PageResponse.of(result, this::toSummary);
    }

    @Transactional(readOnly = true)
    public PublicAnnouncementDetail findVisible(CurrentUser user, Long id) {
        Announcement announcement = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ANNOUNCEMENT_NOT_FOUND"));
        Instant now = Instant.now();
        boolean published = announcement.getStatus() == AnnouncementStatus.PUBLISHED
                && announcement.getPublishAt() != null && !announcement.getPublishAt().isAfter(now);
        if (!published || !audienceMatches(announcement, user)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ANNOUNCEMENT_NOT_FOUND");
        }
        return new PublicAnnouncementDetail(announcement.getId(), announcement.getTitle(), announcement.getSummary(), announcement.getBody(),
                announcement.getPublishDate(), announcement.getExpireDate(), announcement.getPublishTime(), announcement.getExpireTime(),
                announcement.isPinned(),
                announcement.getAudienceType(), sortedRoles(announcement.getAudienceRoles()),
                announcement.getAttachments().stream().map(AttachmentView::of).toList(), isExpired(announcement, now));
    }

    @Transactional(readOnly = true)
    public PageResponse<ManageAnnouncementView> listManaged(CurrentUser user, AnnouncementStatus status, boolean awaitingReview,
                                                            int page, int size) {
        requireManager(user);
        if (awaitingReview) {
            requireAdmin(user);
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Order.desc("updatedOn"), Sort.Order.desc("id")));
        return PageResponse.of(repository.findAll(AnnouncementSpecifications.managedBy(user, status, awaitingReview), pageable),
                this::toManageView);
    }

    @Transactional(readOnly = true)
    public ManageAnnouncementView findManaged(CurrentUser user, Long id) {
        return toManageView(loadManaged(user, id));
    }

    @Transactional(readOnly = true)
    public long countAwaitingReview(CurrentUser user) {
        requireAdmin(user);
        return repository.countAwaitingReview(AnnouncementStatus.PENDING, RevisionStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<HistoryView> history(CurrentUser user, Long id) {
        loadManaged(user, id);
        return historyRepository.findByAnnouncementIdOrderByCreatedOnAsc(id).stream()
                .map(h -> new HistoryView(h.getAction(), h.getActorId(), h.getNote(), h.getCreatedOn())).toList();
    }

    @Transactional
    public ManageAnnouncementView create(CurrentUser user, AnnouncementRequest request) {
        requireManager(user);
        Announcement announcement = new Announcement();
        announcement.setCreatedBy(user.id());
        announcement.setCreatedOn(Instant.now());
        applyRequest(announcement, request);
        announcement.setUpdatedOn(Instant.now());
        Announcement saved = repository.save(announcement);
        syncDocuments(saved, request.attachmentIds(), null, user);
        record(saved, "CREATED", user, null);
        return toManageView(saved);
    }

    @Transactional
    public ManageAnnouncementView update(CurrentUser user, Long id, AnnouncementRequest request) {
        Announcement announcement = loadManaged(user, id);
        switch (announcement.getStatus()) {
            case PENDING -> throw new ApiException(HttpStatus.CONFLICT, "ANNOUNCEMENT_LOCKED");
            case DRAFT, REJECTED, UNPUBLISHED -> {
                applyRequest(announcement, request);
                announcement.setRevision(null);
                announcement.setStatus(AnnouncementStatus.DRAFT);
                announcement.setRejectReason(null);
                syncDocuments(announcement, request.attachmentIds(), null, user);
                record(announcement, "UPDATED", user, null);
            }
            case PUBLISHED -> {
                AnnouncementRevision revision = announcement.getRevision();
                if (revision != null && revision.getStatus() == RevisionStatus.PENDING) {
                    throw new ApiException(HttpStatus.CONFLICT, "ANNOUNCEMENT_LOCKED");
                }
                if (revision == null) {
                    revision = new AnnouncementRevision();
                    revision.setCreatedBy(user.id());
                    announcement.setRevision(revision);
                }
                applyRequest(revision, request);
                revision.setStatus(RevisionStatus.DRAFT);
                revision.setRejectReason(null);
                revision.setUpdatedOn(Instant.now());
                syncDocuments(announcement, idsOf(announcement.getAttachments()), request.attachmentIds(), user);
                record(announcement, "REVISION_UPDATED", user, null);
            }
        }
        announcement.setUpdatedOn(Instant.now());
        return toManageView(announcement);
    }

    @Transactional
    public ManageAnnouncementView submit(CurrentUser user, Long id) {
        Announcement announcement = loadManaged(user, id);
        Instant now = Instant.now();
        AnnouncementRevision revision = announcement.getRevision();
        if (announcement.getStatus() == AnnouncementStatus.DRAFT) {
            if (user.isAdmin()) {
                publish(announcement, user, announcement.isPinRequested());
                record(announcement, "PUBLISHED_BY_ADMIN", user, null);
            } else {
                announcement.setStatus(AnnouncementStatus.PENDING);
                announcement.setSubmittedAt(now);
                record(announcement, "SUBMITTED", user, null);
                notifier.awaitingReview(announcement.getId(), announcement.getTitle(), false);
            }
        } else if (announcement.getStatus() == AnnouncementStatus.PUBLISHED && revision != null
                && revision.getStatus() == RevisionStatus.DRAFT) {
            if (user.isAdmin()) {
                applyRevision(announcement, user, announcement.isPinned());
                record(announcement, "REVISION_PUBLISHED_BY_ADMIN", user, null);
            } else {
                revision.setStatus(RevisionStatus.PENDING);
                revision.setSubmittedAt(now);
                record(announcement, "REVISION_SUBMITTED", user, null);
                notifier.awaitingReview(announcement.getId(), revision.getTitle(), true);
            }
        } else {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        announcement.setUpdatedOn(now);
        return toManageView(announcement);
    }

    @Transactional
    public ManageAnnouncementView withdraw(CurrentUser user, Long id) {
        Announcement announcement = loadManaged(user, id);
        AnnouncementRevision revision = announcement.getRevision();
        if (announcement.getStatus() == AnnouncementStatus.PENDING) {
            announcement.setStatus(AnnouncementStatus.DRAFT);
        } else if (revision != null && revision.getStatus() == RevisionStatus.PENDING) {
            revision.setStatus(RevisionStatus.DRAFT);
        } else {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        record(announcement, "WITHDRAWN", user, null);
        announcement.setUpdatedOn(Instant.now());
        return toManageView(announcement);
    }

    @Transactional
    public ManageAnnouncementView discardRevision(CurrentUser user, Long id) {
        Announcement announcement = loadManaged(user, id);
        if (announcement.getRevision() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        announcement.setRevision(null);
        syncDocuments(announcement, idsOf(announcement.getAttachments()), null, user);
        record(announcement, "REVISION_DISCARDED", user, null);
        announcement.setUpdatedOn(Instant.now());
        return toManageView(announcement);
    }

    @Transactional
    public void delete(CurrentUser user, Long id) {
        Announcement announcement = loadManaged(user, id);
        boolean neverPublished = announcement.getFirstPublishedAt() == null
                && (announcement.getStatus() == AnnouncementStatus.DRAFT || announcement.getStatus() == AnnouncementStatus.REJECTED);
        if (!neverPublished && !user.isAdmin()) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        announcement.setDeleted(true);
        announcement.setPinned(false);
        documentClient.sync(announcement.getId(), List.of(), user.id(), user.roles());
        record(announcement, "DELETED", user, null);
    }

    @Transactional
    public ManageAnnouncementView approve(CurrentUser user, Long id, ReviewDecision decision) {
        requireAdmin(user);
        Announcement announcement = load(id);
        AnnouncementRevision revision = announcement.getRevision();
        if (announcement.getStatus() == AnnouncementStatus.PENDING) {
            boolean pinned = decision != null && decision.pinned() != null ? decision.pinned() : announcement.isPinRequested();
            publish(announcement, user, pinned);
            record(announcement, "APPROVED", user, null);
        } else if (announcement.getStatus() == AnnouncementStatus.PUBLISHED && revision != null
                && revision.getStatus() == RevisionStatus.PENDING) {
            boolean pinned = decision != null && decision.pinned() != null ? decision.pinned() : announcement.isPinned();
            applyRevision(announcement, user, pinned);
            record(announcement, "REVISION_APPROVED", user, null);
        } else {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        announcement.setUpdatedOn(Instant.now());
        return toManageView(announcement);
    }

    @Transactional
    public ManageAnnouncementView reject(CurrentUser user, Long id, ReviewDecision decision) {
        requireAdmin(user);
        String reason = decision == null || decision.reason() == null ? "" : decision.reason().trim();
        if (reason.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REJECT_REASON_REQUIRED");
        }
        if (reason.length() > MAX_REASON) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REJECT_REASON_TOO_LONG");
        }
        Announcement announcement = load(id);
        AnnouncementRevision revision = announcement.getRevision();
        Instant now = Instant.now();
        if (announcement.getStatus() == AnnouncementStatus.PENDING) {
            announcement.setStatus(AnnouncementStatus.REJECTED);
            announcement.setRejectReason(reason);
            announcement.setReviewedBy(user.id());
            announcement.setReviewedAt(now);
            record(announcement, "REJECTED", user, reason);
        } else if (revision != null && revision.getStatus() == RevisionStatus.PENDING) {
            revision.setStatus(RevisionStatus.REJECTED);
            revision.setRejectReason(reason);
            record(announcement, "REVISION_REJECTED", user, reason);
        } else {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        announcement.setUpdatedOn(now);
        return toManageView(announcement);
    }

    @Transactional
    public ManageAnnouncementView unpublish(CurrentUser user, Long id) {
        requireAdmin(user);
        Announcement announcement = load(id);
        if (announcement.getStatus() != AnnouncementStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        announcement.setStatus(AnnouncementStatus.UNPUBLISHED);
        announcement.setPinned(false);
        announcement.setUpdatedOn(Instant.now());
        syncDocuments(announcement, idsOf(announcement.getAttachments()),
                announcement.getRevision() == null ? null : idsOf(announcement.getRevision().getAttachments()), user);
        record(announcement, "UNPUBLISHED", user, null);
        return toManageView(announcement);
    }

    @Transactional
    public ManageAnnouncementView setPinned(CurrentUser user, Long id, boolean pinned) {
        requireAdmin(user);
        Announcement announcement = load(id);
        if (announcement.getStatus() != AnnouncementStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        announcement.setPinned(pinned);
        announcement.setUpdatedOn(Instant.now());
        record(announcement, pinned ? "PINNED" : "UNPINNED", user, null);
        return toManageView(announcement);
    }

    private void publish(Announcement announcement, CurrentUser reviewer, boolean pinned) {
        Instant now = Instant.now();
        if (announcement.getPublishDate() == null) {
            announcement.setPublishDate(LocalDate.now(ZONE));
        }
        applySchedule(announcement);
        announcement.setStatus(AnnouncementStatus.PUBLISHED);
        announcement.setPinned(pinned);
        announcement.setReviewedBy(reviewer.id());
        announcement.setReviewedAt(now);
        announcement.setRejectReason(null);
        if (announcement.getFirstPublishedAt() == null) {
            announcement.setFirstPublishedAt(now);
        }
        syncDocuments(announcement, idsOf(announcement.getAttachments()), null, reviewer);
    }

    private void applyRevision(Announcement announcement, CurrentUser reviewer, boolean pinned) {
        AnnouncementRevision revision = announcement.getRevision();
        announcement.copyFrom(revision);
        announcement.setRevision(null);
        if (announcement.getPublishDate() == null) {
            announcement.setPublishDate(LocalDate.now(ZONE));
        }
        applySchedule(announcement);
        announcement.setPinned(pinned);
        announcement.setReviewedBy(reviewer.id());
        announcement.setReviewedAt(Instant.now());
        syncDocuments(announcement, idsOf(announcement.getAttachments()), null, reviewer);
    }

    private void applySchedule(Announcement announcement) {
        announcement.setPublishAt(startOf(announcement.getPublishDate(), announcement.getPublishTime()));
        announcement.setExpireAt(endOf(announcement.getExpireDate(), announcement.getExpireTime()));
    }

    private static Instant startOf(LocalDate date, LocalTime time) {
        return date.atTime(time == null ? LocalTime.MIDNIGHT : time).atZone(ZONE).toInstant();
    }

    private static Instant endOf(LocalDate date, LocalTime time) {
        if (date == null) {
            return null;
        }
        return time == null ? date.plusDays(1).atStartOfDay(ZONE).toInstant() : date.atTime(time).atZone(ZONE).toInstant();
    }

    private void applyRequest(AnnouncementContent target, AnnouncementRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TITLE_REQUIRED");
        }
        String title = request.title() == null ? "" : request.title().trim();
        if (title.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TITLE_REQUIRED");
        }
        if (title.length() > MAX_TITLE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TITLE_TOO_LONG");
        }
        String summary = request.summary() == null ? "" : request.summary().replaceAll("\\s+", " ").trim();
        if (summary.length() > MAX_SUMMARY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUMMARY_TOO_LONG");
        }
        String body = HtmlSanitizer.sanitize(request.body());
        if (HtmlSanitizer.plainText(body).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BODY_REQUIRED");
        }
        if (body.length() > MAX_BODY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BODY_TOO_LONG");
        }
        if (HtmlSanitizer.imageIds(body).size() > MAX_IMAGES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_IMAGES");
        }
        LocalTime publishTime = request.publishDate() == null ? null : request.publishTime();
        LocalTime expireTime = request.expireDate() == null ? null : request.expireTime();
        if (request.publishDate() != null && request.expireDate() != null
                && !endOf(request.expireDate(), expireTime).isAfter(startOf(request.publishDate(), publishTime))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE");
        }
        if (request.audienceType() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIENCE_REQUIRED");
        }
        Set<String> roles = new LinkedHashSet<>();
        if (request.audienceType() == AudienceType.ROLES) {
            if (request.audienceRoles() != null) {
                request.audienceRoles().stream().filter(Objects::nonNull).map(String::trim).filter(r -> !r.isEmpty()).forEach(roles::add);
            }
            if (roles.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIENCE_ROLES_REQUIRED");
            }
            if (!allowedAudienceRoles.containsAll(roles)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AUDIENCE_ROLE");
            }
        }
        List<String> attachmentIds = request.attachmentIds() == null ? List.of() : request.attachmentIds().stream().distinct().toList();
        if (attachmentIds.size() > MAX_ATTACHMENTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_ATTACHMENTS");
        }
        target.setTitle(title);
        target.setSummary(summary.isEmpty() ? null : summary);
        target.setBody(body);
        target.setPublishDate(request.publishDate());
        target.setExpireDate(request.expireDate());
        target.setPublishTime(publishTime == null ? null : publishTime.withSecond(0).withNano(0));
        target.setExpireTime(expireTime == null ? null : expireTime.withSecond(0).withNano(0));
        target.setPinRequested(Boolean.TRUE.equals(request.pinRequested()));
        target.setAudienceType(request.audienceType());
        target.getAudienceRoles().clear();
        target.getAudienceRoles().addAll(roles);
    }

    private void syncDocuments(Announcement announcement, List<String> liveIds, List<String> revisionIds, CurrentUser actor) {
        List<String> live = liveIds == null ? List.of() : liveIds.stream().distinct().toList();
        List<String> pending = revisionIds == null ? List.of() : revisionIds.stream().distinct().toList();
        boolean visible = announcement.getStatus() == AnnouncementStatus.PUBLISHED;
        String visibility = visible ? switch (announcement.getAudienceType()) {
            case ALL -> "PUBLIC";
            case AUTHENTICATED -> "AUTHENTICATED";
            case ROLES -> "ROLES";
        } : "PRIVATE";
        List<String> roles = visible && announcement.getAudienceType() == AudienceType.ROLES
                ? sortedRoles(announcement.getAudienceRoles()) : List.of();
        List<String> liveImages = HtmlSanitizer.imageIds(announcement.getBody());
        List<String> pendingImages = revisionIds != null && announcement.getRevision() != null
                ? HtmlSanitizer.imageIds(announcement.getRevision().getBody()) : List.of();
        Set<String> liveAll = new LinkedHashSet<>(live);
        liveAll.addAll(liveImages);
        Set<String> pendingAll = new LinkedHashSet<>(pending);
        pendingAll.addAll(pendingImages);
        pendingAll.removeAll(liveAll);
        List<DocumentClient.DocumentAccess> access = new ArrayList<>();
        liveAll.forEach(docId -> access.add(new DocumentClient.DocumentAccess(docId, visibility, roles)));
        pendingAll.forEach(docId -> access.add(new DocumentClient.DocumentAccess(docId, "PRIVATE", List.of())));

        Map<String, DocumentClient.DocumentMetadata> metadata = documentClient
                .sync(announcement.getId(), access, actor.id(), actor.roles()).stream()
                .collect(Collectors.toMap(DocumentClient.DocumentMetadata::id, m -> m, (a, b) -> a));
        Set<String> images = new LinkedHashSet<>(liveImages);
        images.addAll(pendingImages);
        for (String imageId : images) {
            DocumentClient.DocumentMetadata meta = metadata.get(imageId);
            if (meta == null || meta.mimeType() == null || !meta.mimeType().startsWith("image/")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE");
            }
            if (meta.size() != null && meta.size() > MAX_IMAGE_BYTES) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE");
            }
        }

        if (revisionIds == null) {
            replaceAttachments(announcement.getAttachments(), live, metadata);
        } else {
            replaceAttachments(announcement.getAttachments(), live, metadata);
            if (announcement.getRevision() != null) {
                replaceAttachments(announcement.getRevision().getAttachments(), pending, metadata);
            }
        }
    }

    private void replaceAttachments(List<Attachment> target, List<String> ids, Map<String, DocumentClient.DocumentMetadata> metadata) {
        Map<String, Attachment> previous = target.stream().collect(Collectors.toMap(Attachment::getDocumentId, a -> a, (a, b) -> a));
        List<Attachment> next = new ArrayList<>();
        for (String docId : ids) {
            DocumentClient.DocumentMetadata meta = metadata.get(docId);
            if (meta != null) {
                next.add(new Attachment(docId, meta.name(), meta.mimeType(), meta.size()));
            } else if (previous.containsKey(docId)) {
                next.add(previous.get(docId));
            }
        }
        target.clear();
        target.addAll(next);
    }

    private ManageAnnouncementView toManageView(Announcement a) {
        Instant now = Instant.now();
        AnnouncementRevision r = a.getRevision();
        RevisionView revision = r == null ? null : new RevisionView(r.getStatus(), r.getTitle(), r.getSummary(), r.getBody(), r.getPublishDate(),
                r.getExpireDate(), r.getPublishTime(), r.getExpireTime(), r.isPinRequested(), r.getAudienceType(), sortedRoles(r.getAudienceRoles()),
                r.getAttachments().stream().map(AttachmentView::of).toList(), r.getRejectReason(), r.getSubmittedAt(), r.getUpdatedOn());
        boolean scheduled = a.getStatus() == AnnouncementStatus.PUBLISHED && a.getPublishAt() != null && a.getPublishAt().isAfter(now);
        return new ManageAnnouncementView(a.getId(), a.getStatus(), scheduled, isExpired(a, now), a.getTitle(), a.getSummary(), a.getBody(),
                a.getPublishDate(), a.getExpireDate(), a.getPublishTime(), a.getExpireTime(), a.isPinned(), a.isPinRequested(), a.getAudienceType(),
                sortedRoles(a.getAudienceRoles()), a.getAttachments().stream().map(AttachmentView::of).toList(),
                a.getCreatedBy(), a.getCreatedOn(), a.getUpdatedOn(), a.getSubmittedAt(), a.getReviewedAt(), a.getRejectReason(), revision);
    }

    private PublicAnnouncementSummary toSummary(Announcement a) {
        String excerpt = a.getSummary();
        if (excerpt == null || excerpt.isBlank()) {
            String plain = HtmlSanitizer.plainText(a.getBody());
            excerpt = plain.length() > EXCERPT_LENGTH ? plain.substring(0, EXCERPT_LENGTH).trim() + "…" : plain;
        }
        return new PublicAnnouncementSummary(a.getId(), a.getTitle(), excerpt, a.getPublishDate(), a.isPinned(),
                a.getAudienceType(), sortedRoles(a.getAudienceRoles()), a.getAttachments().size());
    }

    private boolean audienceMatches(Announcement a, CurrentUser user) {
        return switch (a.getAudienceType()) {
            case ALL -> true;
            case AUTHENTICATED -> user.isAuthenticated();
            case ROLES -> user.isAuthenticated() && user.hasAnyRole(a.getAudienceRoles());
        };
    }

    private static boolean isExpired(Announcement a, Instant now) {
        return a.getExpireAt() != null && !a.getExpireAt().isAfter(now);
    }

    private static List<String> idsOf(List<Attachment> attachments) {
        return attachments.stream().map(Attachment::getDocumentId).toList();
    }

    private static List<String> sortedRoles(Set<String> roles) {
        return roles.stream().sorted().toList();
    }

    private Announcement load(Long id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ANNOUNCEMENT_NOT_FOUND"));
    }

    private Announcement loadManaged(CurrentUser user, Long id) {
        requireManager(user);
        Announcement announcement = load(id);
        if (!user.isAdmin() && !user.id().equals(announcement.getCreatedBy())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ANNOUNCEMENT_NOT_FOUND");
        }
        return announcement;
    }

    private void requireManager(CurrentUser user) {
        if (!user.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
        if (!user.canManage()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
    }

    private void requireAdmin(CurrentUser user) {
        if (!user.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
        if (!user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
    }

    private void record(Announcement announcement, String action, CurrentUser actor, String note) {
        historyRepository.save(new AnnouncementHistory(announcement.getId(), action, actor.id(), note));
    }

    private static int clampSize(int size) {
        return size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
    }
}
