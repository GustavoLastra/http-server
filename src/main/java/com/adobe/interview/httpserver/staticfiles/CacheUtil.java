package com.adobe.interview.httpserver.staticfiles;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public class CacheUtil {

    public String generateETag(long fileSize, Instant lastModified) {
        return "\"" + Long.toHexString(fileSize) + "-" + Long.toHexString(lastModified.toEpochMilli()) + "\"";
    }

    public String formatHttpDate(Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    public boolean isCurrentVersion(String headerValue, String etag) {
        if (headerValue.trim().equals("*")) {
            return true;
        }
        for (String candidate : headerValue.split(",")) {
            if (candidate.trim().equals(etag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the file has been modified since the given HTTP date header value.
     * On parse failure, returns true (treat as modified).
     */
    public boolean isModifiedSince(Instant lastModified, String headerValue) {
        try {
            Instant since = ZonedDateTime.parse(headerValue.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return lastModified.truncatedTo(ChronoUnit.SECONDS)
                    .isAfter(since.truncatedTo(ChronoUnit.SECONDS));
        } catch (DateTimeParseException e) {
            return true;
        }
    }
}
