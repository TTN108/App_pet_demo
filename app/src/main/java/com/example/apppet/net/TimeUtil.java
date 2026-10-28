package com.example.apppet.net;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Chuyển đổi thời gian ISO 8601 của Supabase (UTC) sang giờ địa phương để hiển thị. */
public final class TimeUtil {
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeUtil() { }

    /** "2026-10-22T08:30:12.123456+00:00" -> "2026-10-22 15:30:12" (theo múi giờ máy). Lỗi -> chuỗi rỗng. */
    public static String isoToLocal(String iso) {
        Instant t = parse(iso);
        return t == null ? "" : LOCAL.format(t.atZone(ZoneId.systemDefault()));
    }

    public static String nowLocal() {
        return LOCAL.format(java.time.ZonedDateTime.now());
    }

    public static Instant parse(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            return OffsetDateTime.parse(iso).toInstant();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** Lùi mốc đồng bộ vài giây để không sót tin commit trễ; trả về ISO UTC. */
    public static String minusSeconds(String iso, long seconds) {
        Instant t = parse(iso);
        return t == null ? null : t.minusSeconds(seconds).toString();
    }

    /** Trả về chuỗi ISO lớn hơn theo thời điểm (bỏ qua null). */
    public static String later(String a, String b) {
        Instant ta = parse(a), tb = parse(b);
        if (ta == null) return b;
        if (tb == null) return a;
        return tb.isAfter(ta) ? b : a;
    }
}
