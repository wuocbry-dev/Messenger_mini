package com.team503.messenger.file.service;

import java.io.InputStream;
import java.net.URI;

public interface ObjectStorageService {

    String putObject(String objectKey, InputStream content, long contentLength, String contentType);

    URI createDownloadUrl(String objectKey);

    void deleteObject(String objectKey);
}
