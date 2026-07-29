package jp.co.housekeeping.person_management.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * TotpService（RFC 6238 TOTP自前実装）のテスト。
 *
 * 最重要: RFC 6238 Appendix B の公式テストベクタで generateCode() の正しさを検証する。
 * ここが通らなければ、自前実装のTOTP計算そのものが誤っていることを意味する。
 */
class TotpServiceTest {

    private final TotpService totpService = new TotpService();

    /** RFC 6238 Appendix B のテストシークレット（ASCIIで "12345678901234567890"、20バイト） */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    // ─── RFC 6238 Appendix B 公式テストベクタ（SHA-1, 8桁）の下6桁 ───

    static Stream<Arguments> rfc6238Vectors() {
        return Stream.of(
            // 時刻(秒), timeStep, RFCの8桁値, 下6桁
            Arguments.of(59L,          0x0000000000000001L, "287082"), // 94287082
            Arguments.of(1111111109L,  0x00000000023523ECL, "081804"), // 07081804
            Arguments.of(1111111111L,  0x00000000023523EDL, "050471"), // 14050471
            Arguments.of(1234567890L,  0x000000000273EF07L, "005924"), // 89005924
            Arguments.of(2000000000L,  0x0000000003F940AAL, "279037"), // 69279037
            Arguments.of(20000000000L, 0x0000000027BC86AAL, "353130")  // 65353130
        );
    }

    @ParameterizedTest(name = "epoch={0} -> 下6桁={2}")
    @MethodSource("rfc6238Vectors")
    void RFC6238のテストベクタで生成したコードの下6桁が一致する(long epochSeconds, long expectedTimeStep, String expectedLast6) {
        // 時刻からtimeStepが正しく計算できることも合わせて確認する
        long timeStep = epochSeconds / TotpService.PERIOD_SECONDS;
        assertEquals(expectedTimeStep, timeStep, "timeStepの計算が想定と異なる");

        // ASCII秘密鍵をBase32化してから元のバイト列に戻し、それをgenerateCodeに渡す
        // （TotpService.toBase32/fromBase32の往復も同時に検証する）
        String base32 = TotpService.toBase32(RFC_SECRET);
        byte[] key = TotpService.fromBase32(base32);

        String actual = totpService.generateCode(key, timeStep);
        assertEquals(expectedLast6, actual);
    }

    // ─── Base32往復変換 ───────────────────────────────────

    @Test
    void base32のtoBase32とfromBase32が往復して元のバイト列に戻る() {
        byte[] original = new byte[20];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) (i * 7 + 3);
        }
        String encoded = TotpService.toBase32(original);
        byte[] decoded = TotpService.fromBase32(encoded);
        assertArrayEquals(original, decoded);
    }

    @Test
    void generateSecretは毎回異なる値を返しBase32デコードすると20バイトになる() {
        String secret1 = totpService.generateSecret();
        String secret2 = totpService.generateSecret();

        assertNotEquals(secret1, secret2);
        assertEquals(20, TotpService.fromBase32(secret1).length);
        assertEquals(20, TotpService.fromBase32(secret2).length);
    }

    // ─── verifyAt（時間ズレの許容・拒否） ───────────────────

    @Test
    void verifyAtは正しいコードを受け入れる() {
        String secret = totpService.generateSecret();
        long epoch = 1_700_000_000L;
        long step = epoch / TotpService.PERIOD_SECONDS;
        String code = totpService.generateCode(TotpService.fromBase32(secret), step);

        assertTrue(totpService.verifyAt(secret, code, epoch));
    }

    @Test
    void verifyAtは1つずれた時間枠のコードも受け入れる() {
        String secret = totpService.generateSecret();
        long epoch = 1_700_000_000L;
        long step = epoch / TotpService.PERIOD_SECONDS;
        String code = totpService.generateCode(TotpService.fromBase32(secret), step);

        // 1つ後ろの時間枠から見て、1つ前(=元のstep)のコードは許容される
        assertTrue(totpService.verifyAt(secret, code, epoch + TotpService.PERIOD_SECONDS));
        // 1つ先の時間枠から見て、1つ後ろ(=元のstep)のコードも許容される
        assertTrue(totpService.verifyAt(secret, code, epoch - TotpService.PERIOD_SECONDS));
    }

    @Test
    void verifyAtは2つ以上ずれた時間枠のコードを拒否する() {
        String secret = totpService.generateSecret();
        long epoch = 1_700_000_000L;
        long step = epoch / TotpService.PERIOD_SECONDS;
        String code = totpService.generateCode(TotpService.fromBase32(secret), step);

        assertFalse(totpService.verifyAt(secret, code, epoch + 2 * TotpService.PERIOD_SECONDS));
        assertFalse(totpService.verifyAt(secret, code, epoch - 2 * TotpService.PERIOD_SECONDS));
    }

    @Test
    void verifyAtは桁数が6桁でない入力を拒否する() {
        String secret = totpService.generateSecret();
        assertFalse(totpService.verifyAt(secret, "12345", 1_700_000_000L));
        assertFalse(totpService.verifyAt(secret, "1234567", 1_700_000_000L));
    }

    @Test
    void verifyAtは数字以外を含む入力を拒否する() {
        String secret = totpService.generateSecret();
        assertFalse(totpService.verifyAt(secret, "12345a", 1_700_000_000L));
    }

    @Test
    void verifyAtはnullを拒否する() {
        String secret = totpService.generateSecret();
        assertFalse(totpService.verifyAt(secret, null, 1_700_000_000L));
        assertFalse(totpService.verifyAt(null, "123456", 1_700_000_000L));
    }

    // ─── otpauth:// URI組み立て ─────────────────────────────

    @Test
    void buildOtpAuthUriがotpauthスキームでsecretとissuerを含む() {
        String secret = totpService.generateSecret();
        String uri = totpService.buildOtpAuthUri("TestIssuer", "alice", secret);

        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("secret=" + secret));
        assertTrue(uri.contains("issuer=TestIssuer"));
        assertTrue(uri.contains("TestIssuer"));
        assertTrue(uri.contains("alice"));
    }
}
