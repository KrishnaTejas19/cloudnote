package com.cloudnote.repo;

import com.cloudnote.model.Note;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, Long> {

    @EntityGraph(attributePaths = "attachments")
    List<Note> findAllByOrderByUpdatedAtDesc();

    @EntityGraph(attributePaths = "attachments")
    Optional<Note> findWithAttachmentsById(Long id);
}
