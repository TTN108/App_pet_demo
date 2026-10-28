package com.example.apppet.net;

import java.util.Locale;

/** Lỗi khi gọi Supabase. status = 0 nghĩa là không kết nối được. */
public class ApiException extends RuntimeException {
    public final int status;
    public final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code == null ? "" : code;
    }

    public boolean isSessionExpired() {
        return "session_expired".equals(code);
    }

    /** Thông báo tiếng Việt cho người dùng. */
    public String userMessage() {
        String m = (getMessage() == null ? "" : getMessage()).toLowerCase(Locale.ROOT);
        String c = code.toLowerCase(Locale.ROOT);
        if (status == 0) return "Không kết nối được máy chủ. Hãy kiểm tra mạng rồi thử lại.";
        if (isSessionExpired()) return "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.";
        if (c.contains("invalid_credentials") || m.contains("invalid login credentials"))
            return "Email/tên đăng nhập hoặc mật khẩu không đúng.";
        if (c.contains("email_not_confirmed") || m.contains("email not confirmed"))
            return "Email chưa được xác nhận. Hãy mở email xác nhận trước khi đăng nhập.";
        if (c.contains("user_already_exists") || m.contains("already registered") || m.contains("already been registered"))
            return "Email này đã được đăng ký tài khoản.";
        if (c.contains("weak_password") || m.contains("password should be"))
            return "Mật khẩu quá yếu hoặc không đạt yêu cầu của máy chủ.";
        if (c.contains("over_email_send_rate_limit") || m.contains("rate limit") || status == 429)
            return "Thao tác quá nhanh, hãy chờ một lúc rồi thử lại.";
        if (m.contains("not_member")) return "Bạn không còn là thành viên của nhóm này.";
        if (m.contains("muted")) return "Bạn đang bị tạm khóa chat trong nhóm này.";
        if (m.contains("rate_limited")) return "Bạn gửi tin quá nhanh, hãy chờ một chút.";
        if (m.contains("message_too_long")) return "Tin nhắn quá dài (tối đa 2000 ký tự).";
        if (m.contains("empty_message")) return "Tin nhắn trống.";
        if (m.contains("not_owner")) return "Bạn không có quyền với thú cưng này.";
        if (m.contains("too_many_pending")) return "Bạn đang có quá nhiều đóng góp chờ duyệt, hãy chờ quản trị viên xử lý bớt.";
        if (m.contains("bad_title")) return "Tiêu đề cần từ 5 đến 120 ký tự.";
        if (m.contains("bad_content")) return "Nội dung cần từ 20 đến 4000 ký tự.";
        if (m.contains("message_not_found")) return "Tin nhắn không còn tồn tại.";
        if (m.contains("bad_breed") || m.contains("bad_category") || m.contains("bad_image_url") || m.contains("bad_origin"))
            return "Dữ liệu đóng góp không hợp lệ.";
        if (m.contains("forbidden")) return "Chỉ quản trị viên mới dùng được chức năng này.";
        if (status == 401 || status == 403) return "Bạn không có quyền thực hiện thao tác này.";
        if (status >= 500) return "Máy chủ đang gặp sự cố, hãy thử lại sau.";
        return "Có lỗi xảy ra (" + status + "). Vui lòng thử lại.";
    }
}
