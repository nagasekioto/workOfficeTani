package jp.co.housekeeping.person_management.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

/**
 * TOTP（時間ベースのワンタイムパスワード / RFC 6238）の生成・照合。
 * Google Authenticator が表示する6桁のコードと同じ計算を行う。
 *
 * 外部ライブラリを使わず自前で実装している。
 * 理由は、RFC 6238 が公式のテストベクタを持っており正しさを機械的に検証できること、
 * および自己完結型パッケージ(packaging/)に不要な依存を持ち込まないため。
 * 実装の正しさは TotpServiceTest で RFC 6238 Appendix B のテストベクタにより検証している。
 */
@Service
public class TotpService {

    /** コードの桁数。Google Authenticator は6桁固定 */
    private static final int DIGITS = 6;

    /** コードが変わる間隔（秒）。Google Authenticator は30秒固定 */
    public static final int PERIOD_SECONDS = 30;

    /**
     * 照合時に許容する前後のズレ（何個ぶんの時間枠まで認めるか）。
     * 1 なら「1つ前・現在・1つ後」の3つを認める＝前後30秒のズレを許容する。
     *
     * 0 にするとPCとスマホの時計が数秒ずれただけでログインできなくなり、
     * 大きくするほど盗み見られたコードが使える時間が延びる。1が一般的な妥協点。
     */
    private static final int ALLOWED_DRIFT_STEPS = 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Base32（RFC 4648）の文字表。Google Authenticator の鍵はこの形式 */
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    // ─── 鍵の生成 ──────────────────────────────────────────

    /**
     * 新しいTOTPシークレットを生成し、Base32文字列で返す。
     * 160ビット（20バイト）はRFC 4226が推奨する長さ。
     */
    public String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return toBase32(bytes);
    }

    /**
     * Google Authenticator に読み込ませるための otpauth:// URI を組み立てる。
     * この文字列をQRコードにして画面に表示する。
     *
     * @param issuer    サービス名（アプリ上の表示名になる）
     * @param accountName 利用者名（同じサービスの複数アカウントを見分けるため）
     * @param secretBase32 generateSecret() で作った鍵
     */
    public String buildOtpAuthUri(String issuer, String accountName, String secretBase32) {
        String enc = java.net.URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String encAccount = java.net.URLEncoder.encode(accountName, StandardCharsets.UTF_8);
        return "otpauth://totp/" + enc + ":" + encAccount
             + "?secret=" + secretBase32
             + "&issuer=" + enc
             + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    // ─── 照合 ──────────────────────────────────────────────

    /**
     * 入力された6桁コードが正しいか照合する。
     * 時計のズレを考慮し、前後 ALLOWED_DRIFT_STEPS 個ぶんの時間枠も認める。
     */
    public boolean verify(String secretBase32, String code) {
        return verifyAt(secretBase32, code, Instant.now().getEpochSecond());
    }

    /** テストから任意の時刻を指定して照合するための入口 */
    public boolean verifyAt(String secretBase32, String code, long epochSeconds) {
        if (secretBase32 == null || secretBase32.isBlank() || code == null) {
            return false;
        }
        String normalized = code.trim().replace(" ", "");
        if (normalized.length() != DIGITS || !normalized.chars().allMatch(Character::isDigit)) {
            return false;
        }
        byte[] key;
        try {
            key = fromBase32(secretBase32);
        } catch (Exception e) {
            return false;
        }
        long currentStep = epochSeconds / PERIOD_SECONDS;
        for (int drift = -ALLOWED_DRIFT_STEPS; drift <= ALLOWED_DRIFT_STEPS; drift++) {
            String expected = generateCode(key, currentStep + drift);
            // 総当たりで1桁ずつ正解を絞られないよう、桁数に依存しない比較を行う
            if (constantTimeEquals(expected, normalized)) {
                return true;
            }
        }
        return false;
    }

    /** 指定した時間枠のコードを生成する（RFC 6238 / HOTP=RFC 4226） */
    public String generateCode(byte[] key, long timeStep) {
        byte[] data = new byte[8];
        long value = timeStep;
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        byte[] hash;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            hash = mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("TOTPの計算に失敗しました", e);
        }
        // 動的切り捨て（RFC 4226 section 5.3）
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset]     & 0x7F) << 24)
                   | ((hash[offset + 1] & 0xFF) << 16)
                   | ((hash[offset + 2] & 0xFF) << 8)
                   |  (hash[offset + 3] & 0xFF);
        int otp = binary % (int) Math.pow(10, DIGITS);
        return String.format("%0" + DIGITS + "d", otp);
    }

    // ─── Base32（RFC 4648） ─────────────────────────────────

    static String toBase32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32_CHARS.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    static byte[] fromBase32(String base32) {
        String s = base32.trim().replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : s.toCharArray()) {
            int idx = BASE32_CHARS.indexOf(c);
            if (idx < 0) {
                throw new IllegalArgumentException("Base32として不正な文字が含まれています");
            }
            buffer = (buffer << 5) | idx;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    /** 比較にかかる時間から正解の桁数を推測されないようにするための比較 */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
