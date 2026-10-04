package com.curea.announcement.service;

import com.curea.announcement.config.CurrentUser;
import com.curea.announcement.domain.Announcement;
import com.curea.announcement.domain.AnnouncementStatus;
import com.curea.announcement.domain.AudienceType;
import com.curea.announcement.domain.RevisionStatus;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AnnouncementSpecifications {

    public enum Scope { ALL, HOME, TARGETED }

    private AnnouncementSpecifications() {
    }

    public static Specification<Announcement> visibleTo(CurrentUser user, Scope scope, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            predicates.add(cb.equal(root.get("status"), AnnouncementStatus.PUBLISHED));
            predicates.add(cb.lessThanOrEqualTo(root.get("publishAt"), now));
            predicates.add(cb.or(cb.isNull(root.get("expireAt")), cb.greaterThan(root.get("expireAt"), now)));
            predicates.add(audience(root, query, cb, user, scope));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Predicate audience(Root<Announcement> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                     CurrentUser user, Scope scope) {
        Predicate all = cb.equal(root.get("audienceType"), AudienceType.ALL);
        if (scope == Scope.HOME || !user.isAuthenticated()) {
            return all;
        }
        Predicate authenticated = cb.equal(root.get("audienceType"), AudienceType.AUTHENTICATED);
        Predicate roles = cb.disjunction();
        if (!user.roles().isEmpty()) {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Announcement> inner = sub.from(Announcement.class);
            Join<Announcement, String> role = inner.join("audienceRoles");
            sub.select(inner.get("id")).where(cb.equal(inner.get("id"), root.get("id")), role.in(user.roles()));
            roles = cb.and(cb.equal(root.get("audienceType"), AudienceType.ROLES), cb.exists(sub));
        }
        Predicate targeted = cb.or(authenticated, roles);
        return scope == Scope.TARGETED ? targeted : cb.or(all, targeted);
    }

    public static Specification<Announcement> matchesText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + normalize(text.trim()) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(normalized(cb, root.get("title")), pattern),
                cb.like(normalized(cb, root.get("body")), pattern));
    }

    public static Specification<Announcement> publishedInYear(Integer year, ZoneId zone) {
        if (year == null) {
            return null;
        }
        Instant from = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant();
        Instant to = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant();
        return (root, query, cb) -> cb.and(cb.greaterThanOrEqualTo(root.get("publishAt"), from), cb.lessThan(root.get("publishAt"), to));
    }

    public static Specification<Announcement> pinnedOnly(boolean pinnedOnly) {
        return pinnedOnly ? (root, query, cb) -> cb.isTrue(root.get("pinned")) : null;
    }

    public static Specification<Announcement> managedBy(CurrentUser user, AnnouncementStatus status, boolean awaitingReview) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (!user.isAdmin()) {
                predicates.add(cb.equal(root.get("createdBy"), user.id()));
            }
            if (awaitingReview) {
                Join<Object, Object> revision = root.join("revision", JoinType.LEFT);
                predicates.add(cb.or(cb.equal(root.get("status"), AnnouncementStatus.PENDING),
                        cb.equal(revision.get("status"), RevisionStatus.PENDING)));
            } else if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Expression<String> normalized(CriteriaBuilder cb, Expression<String> value) {
        return cb.function("translate", String.class, cb.lower(value), cb.literal("İIıÇŞĞÜÖ"), cb.literal("iiiçşğüö"));
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.forLanguageTag("tr")).replace('ı', 'i')
                .replace("%", "\\%").replace("_", "\\_");
    }
}
