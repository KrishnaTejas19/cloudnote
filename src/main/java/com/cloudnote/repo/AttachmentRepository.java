package com.cloudnote.repo;

import com.cloudnote.model.Attachment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    Optional<Attachment> findByIdAndNoteId(Long id, Long noteId);
}
