package com.cloudnote.web;

import com.cloudnote.service.NoteService;
import com.cloudnote.web.Dtos.AttachmentDto;
import com.cloudnote.web.Dtos.NoteDto;
import com.cloudnote.web.Dtos.NoteRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService service;

    public NoteController(NoteService service) {
        this.service = service;
    }

    @GetMapping
    public List<NoteDto> list() {
        return service.list().stream().map(NoteDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteDto create(@Valid @RequestBody NoteRequest req) {
        return NoteDto.of(service.create(req.title(), req.content()));
    }

    @GetMapping("/{id}")
    public NoteDto get(@PathVariable Long id) {
        return NoteDto.of(service.get(id));
    }

    @PutMapping("/{id}")
    public NoteDto update(@PathVariable Long id, @Valid @RequestBody NoteRequest req) {
        return NoteDto.of(service.update(id, req.title(), req.content()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentDto upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return AttachmentDto.of(service.addAttachment(id, file));
    }

    @GetMapping("/{id}/attachments/{fileId}")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id, @PathVariable Long fileId) {
        var d = service.download(id, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.meta().getContentType()))
                .contentLength(d.meta().getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.meta().getFileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(d.stream()));
    }
}
