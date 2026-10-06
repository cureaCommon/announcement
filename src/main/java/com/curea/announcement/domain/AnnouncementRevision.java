package com.curea.announcement.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "announcement_revision")
public class AnnouncementRevision implements AnnouncementContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RevisionStatus status = RevisionStatus.DRAFT;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "summary", length = 250)
    private String summary;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "publish_date")
    private LocalDate publishDate;

    @Column(name = "expire_date")
    private LocalDate expireDate;

    @Column(name = "pin_requested", nullable = false)
    private boolean pinRequested;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false, length = 20)
    private AudienceType audienceType = AudienceType.ALL;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "announcement_revision_audience_role", joinColumns = @JoinColumn(name = "revision_id"))
    @Column(name = "role_name", length = 50)
    private Set<String> audienceRoles = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "announcement_revision_attachment", joinColumns = @JoinColumn(name = "revision_id"))
    @OrderColumn(name = "position")
    private List<Attachment> attachments = new ArrayList<>();

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_on")
    private Instant updatedOn;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reject_reason", length = 1000)
    private String rejectReason;

    public Long getId() { return id; }
    public RevisionStatus getStatus() { return status; }
    public void setStatus(RevisionStatus status) { this.status = status; }
    @Override public String getSummary() { return summary; }
    @Override public void setSummary(String summary) { this.summary = summary; }
    @Override public String getTitle() { return title; }
    @Override public void setTitle(String title) { this.title = title; }
    @Override public String getBody() { return body; }
    @Override public void setBody(String body) { this.body = body; }
    @Override public LocalDate getPublishDate() { return publishDate; }
    @Override public void setPublishDate(LocalDate publishDate) { this.publishDate = publishDate; }
    @Override public LocalDate getExpireDate() { return expireDate; }
    @Override public void setExpireDate(LocalDate expireDate) { this.expireDate = expireDate; }
    @Override public boolean isPinRequested() { return pinRequested; }
    @Override public void setPinRequested(boolean pinRequested) { this.pinRequested = pinRequested; }
    @Override public AudienceType getAudienceType() { return audienceType; }
    @Override public void setAudienceType(AudienceType audienceType) { this.audienceType = audienceType; }
    @Override public Set<String> getAudienceRoles() { return audienceRoles; }
    @Override public List<Attachment> getAttachments() { return attachments; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getUpdatedOn() { return updatedOn; }
    public void setUpdatedOn(Instant updatedOn) { this.updatedOn = updatedOn; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
}
