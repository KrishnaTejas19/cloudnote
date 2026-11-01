package com.cloudnote.storage;

import java.io.InputStream;

/** Where uploaded files live. S3 in prod, local disk in the "local" profile. */
public interface FileStorage {
    void put(String key, InputStream data, long size, String contentType);

    InputStream get(String key);

    void delete(String key);
}
