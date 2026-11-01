package com.cloudnote.web;

import com.cloudnote.model.Attachment;
import com.cloudnote.model.Note;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    public record NoteRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 20000) String content) {}

    public record AttachmentDto(Long id, String fileName, String contentType, long size) {
        static AttachmentDto of(Attachment a) {
            return new AttachmentDto(a.getId(), a.getFileName(), a.getContentType(), a.getSize());
        }
    }

    public record NoteDto(Long id, String title, String content, Instant createdAt, Instant updatedAt,
                          List<AttachmentDto> attachments) {
        static NoteDto of(Note n) {
            return new NoteDto(n.getId(), n.getTitle(), n.getContent(), n.getCreatedAt(), n.getUpdatedAt(),
                    n.getAttachments().stream().map(AttachmentDto::of).toList());
        }
    }
}
