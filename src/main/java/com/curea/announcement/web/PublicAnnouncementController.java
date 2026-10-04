package com.curea.announcement.web;

import com.curea.announcement.config.CurrentUser;
import com.curea.announcement.service.AnnouncementService;
import com.curea.announcement.service.AnnouncementSpecifications.Scope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/announcements/public")
public class PublicAnnouncementController {

    private final AnnouncementService service;

    public PublicAnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PublicAnnouncementSummary> list(@RequestParam(defaultValue = "ALL") Scope scope,
                                                        @RequestParam(required = false) String q,
                                                        @RequestParam(required = false) Integer year,
                                                        @RequestParam(defaultValue = "false") boolean pinnedOnly,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "10") int size,
                                                        HttpServletRequest request) {
        return service.listVisible(CurrentUser.from(request), scope, q, year, pinnedOnly, page, size);
    }

    @GetMapping("/{id}")
    public PublicAnnouncementDetail detail(@PathVariable Long id, HttpServletRequest request) {
        return service.findVisible(CurrentUser.from(request), id);
    }
}
