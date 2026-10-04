package com.curea.announcement.web;

import com.curea.announcement.config.CurrentUser;
import com.curea.announcement.service.AnnouncementService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/announcements/admin")
public class AdminAnnouncementController {

    private final AnnouncementService service;

    public AdminAnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping("/awaiting-review/count")
    public Map<String, Long> awaitingReviewCount(HttpServletRequest request) {
        return Map.of("count", service.countAwaitingReview(CurrentUser.from(request)));
    }

    @PostMapping("/{id}/approve")
    public ManageAnnouncementView approve(@PathVariable Long id, @RequestBody(required = false) ReviewDecision decision,
                                          HttpServletRequest request) {
        return service.approve(CurrentUser.from(request), id, decision);
    }

    @PostMapping("/{id}/reject")
    public ManageAnnouncementView reject(@PathVariable Long id, @RequestBody(required = false) ReviewDecision decision,
                                         HttpServletRequest request) {
        return service.reject(CurrentUser.from(request), id, decision);
    }

    @PostMapping("/{id}/unpublish")
    public ManageAnnouncementView unpublish(@PathVariable Long id, HttpServletRequest request) {
        return service.unpublish(CurrentUser.from(request), id);
    }

    @PostMapping("/{id}/pin")
    public ManageAnnouncementView pin(@PathVariable Long id, @RequestParam boolean pinned, HttpServletRequest request) {
        return service.setPinned(CurrentUser.from(request), id, pinned);
    }
}
