package com.curea.announcement.web;

import com.curea.announcement.config.CurrentUser;
import com.curea.announcement.domain.AnnouncementStatus;
import com.curea.announcement.service.AnnouncementService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/announcements/manage")
public class ManageAnnouncementController {

    private final AnnouncementService service;

    public ManageAnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ManageAnnouncementView> list(@RequestParam(required = false) AnnouncementStatus status,
                                                     @RequestParam(defaultValue = "false") boolean awaitingReview,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     HttpServletRequest request) {
        return service.listManaged(CurrentUser.from(request), status, awaitingReview, page, size);
    }

    @GetMapping("/{id}")
    public ManageAnnouncementView get(@PathVariable Long id, HttpServletRequest request) {
        return service.findManaged(CurrentUser.from(request), id);
    }

    @GetMapping("/{id}/history")
    public List<HistoryView> history(@PathVariable Long id, HttpServletRequest request) {
        return service.history(CurrentUser.from(request), id);
    }

    @PostMapping
    public ManageAnnouncementView create(@RequestBody AnnouncementRequest body, HttpServletRequest request) {
        return service.create(CurrentUser.from(request), body);
    }

    @PutMapping("/{id}")
    public ManageAnnouncementView update(@PathVariable Long id, @RequestBody AnnouncementRequest body, HttpServletRequest request) {
        return service.update(CurrentUser.from(request), id, body);
    }

    @PostMapping("/{id}/submit")
    public ManageAnnouncementView submit(@PathVariable Long id, HttpServletRequest request) {
        return service.submit(CurrentUser.from(request), id);
    }

    @PostMapping("/{id}/withdraw")
    public ManageAnnouncementView withdraw(@PathVariable Long id, HttpServletRequest request) {
        return service.withdraw(CurrentUser.from(request), id);
    }

    @DeleteMapping("/{id}/revision")
    public ManageAnnouncementView discardRevision(@PathVariable Long id, HttpServletRequest request) {
        return service.discardRevision(CurrentUser.from(request), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        service.delete(CurrentUser.from(request), id);
        return ResponseEntity.noContent().build();
    }
}
