package com.cloudnote.service;

import com.cloudnote.model.Attachment;
import com.cloudnote.model.Note;
import com.cloudnote.repo.AttachmentRepository;
import com.cloudnote.repo.NoteRepository;
import com.cloudnote.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class NoteService {

    private static final Logger log = LoggerFactory.getLogger(NoteService.class);

    /** Allowlist: extension -> content type we store and serve (we never trust the browser's value). */
    private static final Map<String, String> ALLOWED = Map.of(
            "txt", "text/plain",
            "md", "text/markdown",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp");

    private final NoteRepository notes;
    private final AttachmentRepository attachments;
    private final FileStorage storage;
    private final long maxFileBytes;
    private final int maxAttachments;

    public NoteService(NoteRepository notes,
                       AttachmentRepository attachments,
                       FileStorage storage,
                       @Value("${cloudnote.max-file-bytes}") long maxFileBytes,
                       @Value("${cloudnote.max-attachments-per-note}") int maxAttachments) {
        this.notes = notes;
        this.attachments = attachments;
        this.storage = storage;
        this.maxFileBytes = maxFileBytes;
        this.maxAttachments = maxAttachments;
    }

    @Transactional(readOnly = true)
    public List<Note> list() {
        return notes.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Note get(Long id) {
        return notes.findWithAttachmentsById(id).orElseThrow(() -> new NotFoundException("Note " + id + " not found"));
    }

    @Transactional
    public Note create(String title, String content) {
        Note n = new Note();
        n.setTitle(title.trim());
        n.setContent(content);
        return notes.save(n);
    }

    @Transactional
    public Note update(Long id, String title, String content) {
        Note n = get(id);
        n.setTitle(title.trim());
        n.setContent(content);
        return n;
    }

    @Transactional
    public void delete(Long id) {
        Note n = get(id);
        List<String> keys = n.getAttachments().stream().map(Attachment::getStorageKey).toList();
        notes.delete(n);
        // Remove files only after the DB delete has really committed.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                keys.forEach(NoteService.this::deleteFileQuietly);
            }
        });
    }

    @Transactional
    public Attachment addAttachment(Long noteId, MultipartFile file) {
        Note note = get(noteId);
        if (note.getAttachments().size() >= maxAttachments) {
            throw new BadRequestException("A note can have at most " + maxAttachments + " attachments");
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        if (file.getSize() > maxFileBytes) {
            throw new BadRequestException("File is larger than " + (maxFileBytes / 1024 / 1024) + " MB");
        }

        String safeName = sanitizeFileName(file.getOriginalFilename());
        String ext = extension(safeName);
        String contentType = ALLOWED.get(ext);
        if (contentType == null) {
            throw new BadRequestException("Only .txt, .md, .png, .jpg, .gif and .webp files are allowed");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        verifyContent(ext, bytes);

        String key = "notes/" + noteId + "/" + UUID.randomUUID() + "-" + safeName;
        storage.put(key, new ByteArrayInputStream(bytes), bytes.length, contentType);
        try {
            Attachment a = attachments.save(new Attachment(note, safeName, contentType, bytes.length, key));
            note.getAttachments().add(a);
            return a;
        } catch (RuntimeException e) {
            deleteFileQuietly(key); // don't leave an orphan file behind if the DB insert failed
            throw e;
        }
    }

    public record Download(Attachment meta, InputStream stream) {}

    @Transactional(readOnly = true)
    public Download download(Long noteId, Long fileId) {
        Attachment a = attachments.findByIdAndNoteId(fileId, noteId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        return new Download(a, storage.get(a.getStorageKey()));
    }

    // ---- helpers -------------------------------------------------------------------------

    private void deleteFileQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete stored file {}: {}", key, e.getMessage());
        }
    }

    static String sanitizeFileName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.length() > 100) {
            String ext = extension(name);
            name = name.substring(0, 90) + (ext.isEmpty() ? "" : "." + ext);
        }
        return name.isBlank() ? "file" : name;
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Cheap content check so a renamed .exe cannot pass as .png or .txt. */
    private static void verifyContent(String ext, byte[] b) {
        boolean ok = switch (ext) {
            case "png" -> startsWith(b, 0x89, 'P', 'N', 'G');
            case "jpg", "jpeg" -> startsWith(b, 0xFF, 0xD8, 0xFF);
            case "gif" -> startsWith(b, 'G', 'I', 'F', '8');
            case "webp" -> b.length > 12 && startsWith(b, 'R', 'I', 'F', 'F') && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> noNulBytes(b); // txt / md must be text
        };
        if (!ok) {
            throw new BadRequestException("File content does not match its extension");
        }
    }

    private static boolean startsWith(byte[] b, int... sig) {
        if (b.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) {
            if ((b[i] & 0xFF) != sig[i]) return false;
        }
        return true;
    }

    private static boolean noNulBytes(byte[] b) {
        for (byte x : b) {
            if (x == 0) return false;
        }
        return true;
    }
}
