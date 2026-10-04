package com.booking.controller;

import com.booking.dto.CreateShowRequest;
import com.booking.dto.ShowResponse;
import com.booking.service.ShowServiceInterface;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final ShowServiceInterface showService;

    public ShowController(ShowServiceInterface showService) {
        this.showService = showService;
    }

    @PostMapping
    public ResponseEntity<ShowResponse> createShow(@Valid @RequestBody CreateShowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(showService.createShow(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShowResponse> getShow(@PathVariable Long id) {
        return ResponseEntity.ok(showService.getShow(id));
    }
}
