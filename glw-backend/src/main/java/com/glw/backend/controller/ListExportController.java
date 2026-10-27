package com.glw.backend.controller;

import com.glw.backend.model.dto.ListExportDTO;
import com.glw.backend.service.ListExportService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/exports")
@Profile("!mock")
@AllArgsConstructor
public class ListExportController {
    private final ListExportService exports;

    @PostMapping("/{kind}")
    public ResponseEntity<byte[]> download(@PathVariable String kind, @Valid @RequestBody ListExportDTO query) {
        var result = exports.export(kind, query);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(result.name() + "-" + LocalDate.now() + ".csv", StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.content());
    }
}
