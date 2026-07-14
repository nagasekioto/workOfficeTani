package jp.co.housekeeping.person_management.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * メールアドレス宛の認証コード(OTP)によるログイン認証を行うサービス。
 *
 * 現時点では app.auth.email-enabled=false がデフォルトのため、
 * このクラスのメソッドはどこからも呼び出されない(準備のみ・未稼働)。
 * 将来 email-enabled を true にする際は、LoginController側で
 * このサービスを使ったログインフローが有効になる。
 */
@Service
public class EmailAuthService {

    private final JavaMailSender mailSender;
    private final List<String> allowedEmails;
    private final long otpExpiryMillis;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static class OtpEntry {
        final String code;
        final long expiresAt;

        OtpEntry(String code, long expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }

    // メールアドレスごとに発行済みコードを保持(サーバー再起動でクリアされる簡易実装)
    private final Map<String, OtpEntry> pendingCodes = new ConcurrentHashMap<>();

    public EmailAuthService(JavaMailSender mailSender,
                             @Value("${app.auth.allowed-emails:}") List<String> allowedEmails,
                             @Value("${app.auth.otp-expiry-seconds:300}") long otpExpirySeconds) {
        this.mailSender = mailSender;
        this.allowedEmails = allowedEmails;
        this.otpExpiryMillis = otpExpirySeconds * 1000L;
    }

    /** 指定されたメールアドレスがログインを許可されたアドレスかどうか */
    public boolean isAllowed(String email) {
        return email != null && allowedEmails.stream()
                .anyMatch(allowed -> allowed.equalsIgnoreCase(email.trim()));
    }

    /** 6桁の認証コードを発行し、メール送信する */
    public void sendCode(String email) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        long expiresAt = System.currentTimeMillis() + otpExpiryMillis;
        pendingCodes.put(email.trim().toLowerCase(), new OtpEntry(code, expiresAt));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("【ワークオフィス谷】ログイン認証コード");
        message.setText(
                "ログイン用の認証コードです。\n\n" +
                "認証コード: " + code + "\n\n" +
                "このコードの有効期限は" + (otpExpiryMillis / 1000 / 60) + "分間です。\n" +
                "心当たりがない場合はこのメールを破棄してください。"
        );
        mailSender.send(message);
    }

    /** 認証コードを照合する。一致すればtrueを返し、そのコードは使用済みとして破棄する */
    public boolean verifyCode(String email, String code) {
        if (email == null || code == null) {
            return false;
        }
        String key = email.trim().toLowerCase();
        OtpEntry entry = pendingCodes.get(key);
        if (entry == null) {
            return false;
        }
        boolean valid = System.currentTimeMillis() <= entry.expiresAt
                && entry.code.equals(code.trim());
        if (valid) {
            pendingCodes.remove(key); // 使い回し防止のため一度使ったコードは破棄
        }
        return valid;
    }
}
