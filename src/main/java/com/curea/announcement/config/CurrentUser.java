package com.curea.announcement.config;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public record CurrentUser(Long id, Set<String> roles) {

    public static final String USER_HEADER = "X-User";
    public static final String ROLES_HEADER = "X-User-Roles";
    public static final String ADMIN = "ADMIN";
    public static final String EDITOR = "ANNOUNCEMENT_EDITOR";

    public static CurrentUser from(HttpServletRequest request) {
        Long id = null;
        String rawId = request.getHeader(USER_HEADER);
        if (rawId != null && !rawId.isBlank()) {
            try {
                id = Long.valueOf(rawId.trim());
            } catch (NumberFormatException ignored) {
                id = null;
            }
        }
        String rawRoles = request.getHeader(ROLES_HEADER);
        Set<String> roles = rawRoles == null || rawRoles.isBlank()
                ? Collections.emptySet()
                : Arrays.stream(rawRoles.split(",")).map(String::trim).filter(r -> !r.isEmpty()).collect(Collectors.toUnmodifiableSet());
        return new CurrentUser(id, id == null ? Collections.emptySet() : roles);
    }

    public boolean isAuthenticated() {
        return id != null;
    }

    public boolean isAdmin() {
        return roles.contains(ADMIN);
    }

    public boolean isEditor() {
        return roles.contains(EDITOR);
    }

    public boolean canManage() {
        return isAdmin() || isEditor();
    }

    public boolean hasAnyRole(Set<String> required) {
        return required.stream().anyMatch(roles::contains);
    }
}
