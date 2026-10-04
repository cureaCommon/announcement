package com.curea.announcement.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "announcement", indexes = {
        @Index(name = "ix_announcement_status_publish", columnList = "status,publish_at"),
        @Index(name = "ix_announcement_created_by", columnList = "created_by")
})
public class Announcement implements AnnouncementContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnnouncementStatus status = AnnouncementStatus.DRAFT;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "publish_date")
    private LocalDate publishDate;

    @Column(name = "expire_date")
    private LocalDate expireDate;

    @Column(name = "publish_at")
    private Instant publishAt;

    @Column(name = "expire_at")
    private Instant expireAt;

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Column(name = "pin_requested", nullable = false)
    private boolean pinRequested;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false, length = 20)
    private AudienceType audienceType = AudienceType.ALL;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "announcement_audience_role", joinColumns = @JoinColumn(name = "announcement_id"))
    @Column(name = "role_name", length = 50)
    private Set<String> audienceRoles = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "announcement_attachment", joinColumns = @JoinColumn(name = "announcement_id"))
    @OrderColumn(name = "position")
    private List<Attachment> attachments = new ArrayList<>();

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "revision_id")
    private AnnouncementRevision revision;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_on", nullable = false)
    private Instant createdOn;

    @Column(name = "updated_on")
    private Instant updatedOn;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reject_reason", length = 1000)
    private String rejectReason;

    @Column(name = "first_published_at")
    private Instant firstPublishedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    public Long getId() { return id; }
    public AnnouncementStatus getStatus() { return status; }
    public void setStatus(AnnouncementStatus status) { this.status = status; }
    @Override public String getTitle() { return title; }
    @Override public void setTitle(String title) { this.title = title; }
    @Override public String getBody() { return body; }
    @Override public void setBody(String body) { this.body = body; }
    @Override public LocalDate getPublishDate() { return publishDate; }
    @Override public void setPublishDate(LocalDate publishDate) { this.publishDate = publishDate; }
    @Override public LocalDate getExpireDate() { return expireDate; }
    @Override public void setExpireDate(LocalDate expireDate) { this.expireDate = expireDate; }
    public Instant getPublishAt() { return publishAt; }
    public void setPublishAt(Instant publishAt) { this.publishAt = publishAt; }
    public Instant getExpireAt() { return expireAt; }
    public void setExpireAt(Instant expireAt) { this.expireAt = expireAt; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    @Override public boolean isPinRequested() { return pinRequested; }
    @Override public void setPinRequested(boolean pinRequested) { this.pinRequested = pinRequested; }
    @Override public AudienceType getAudienceType() { return audienceType; }
    @Override public void setAudienceType(AudienceType audienceType) { this.audienceType = audienceType; }
    @Override public Set<String> getAudienceRoles() { return audienceRoles; }
    @Override public List<Attachment> getAttachments() { return attachments; }
    public AnnouncementRevision getRevision() { return revision; }
    public void setRevision(AnnouncementRevision revision) { this.revision = revision; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedOn() { return createdOn; }
    public void setCreatedOn(Instant createdOn) { this.createdOn = createdOn; }
    public Instant getUpdatedOn() { return updatedOn; }
    public void setUpdatedOn(Instant updatedOn) { this.updatedOn = updatedOn; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public Instant getFirstPublishedAt() { return firstPublishedAt; }
    public void setFirstPublishedAt(Instant firstPublishedAt) { this.firstPublishedAt = firstPublishedAt; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
