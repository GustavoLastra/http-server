package com.adobe.interview.httpserver.staticfiles;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CacheUtilTest {

    private final CacheUtil cacheUtil = new CacheUtil();

    @Test
    void generateETagIsQuoted() {
        String etag = cacheUtil.generateETag(100, Instant.ofEpochMilli(1000));
        assertTrue(etag.startsWith("\""));
        assertTrue(etag.endsWith("\""));
    }

    @Test
    void generateETagIsDeterministic() {
        Instant ts = Instant.ofEpochMilli(123456789);
        assertEquals(cacheUtil.generateETag(42, ts), cacheUtil.generateETag(42, ts));
    }

    @Test
    void generateETagDiffersOnSizeChange() {
        Instant ts = Instant.ofEpochMilli(0);
        assertNotEquals(cacheUtil.generateETag(1, ts), cacheUtil.generateETag(2, ts));
    }

    @Test
    void formatHttpDateProducesRfc1123() {
        String date = cacheUtil.formatHttpDate(Instant.ofEpochMilli(0));
        assertEquals("Thu, 1 Jan 1970 00:00:00 GMT", date);
    }

    @Test
    void isCurrentVersionExact() {
        assertTrue(cacheUtil.isCurrentVersion("\"abc\"", "\"abc\""));
    }

    @Test
    void isCurrentVersionWildcard() {
        assertTrue(cacheUtil.isCurrentVersion("*", "\"abc\""));
    }

    @Test
    void isCurrentVersionOneOfMany() {
        assertTrue(cacheUtil.isCurrentVersion("\"x\", \"abc\", \"y\"", "\"abc\""));
    }

    @Test
    void isCurrentVersionReturnsFalseWhenDifferent() {
        assertFalse(cacheUtil.isCurrentVersion("\"other\"", "\"abc\""));
    }

    @Test
    void isModifiedSinceReturnsTrueWhenFileIsNewer() {
        Instant lastModified = Instant.parse("2024-06-01T12:00:00Z");
        String since = cacheUtil.formatHttpDate(Instant.parse("2024-01-01T00:00:00Z"));
        assertTrue(cacheUtil.isModifiedSince(lastModified, since));
    }

    @Test
    void isModifiedSinceReturnsFalseWhenFileIsOlder() {
        Instant lastModified = Instant.parse("2024-01-01T00:00:00Z");
        String since = cacheUtil.formatHttpDate(Instant.parse("2024-06-01T12:00:00Z"));
        assertFalse(cacheUtil.isModifiedSince(lastModified, since));
    }

    @Test
    void isModifiedSinceReturnsTrueOnInvalidDate() {
        assertTrue(cacheUtil.isModifiedSince(Instant.now(), "not-a-date"));
    }
}
