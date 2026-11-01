package com.cloudnote.model;

import jakarta.persistence.*;

@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_id", nullable = false)
    private Note note;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long size;

    /** Object key in S3 (or relative path in local mode). Only the key is stored in the DB. */
    @Column(nullable = false, length = 512)
    private String storageKey;

    protected Attachment() {}

    public Attachment(Note note, String fileName, String contentType, long size, String storageKey) {
        this.note = note;
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
        this.storageKey = storageKey;
    }

    public Long getId() { return id; }
    public Note getNote() { return note; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }
    public String getStorageKey() { return storageKey; }
}
