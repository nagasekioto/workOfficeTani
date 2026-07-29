package jp.co.housekeeping.person_management.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import jp.co.housekeeping.person_management.model.AuthBackupCode;
import jp.co.housekeeping.person_management.model.AuthUser;
import jp.co.housekeeping.person_management.repository.AuthBackupCodeRepository;
import jp.co.housekeeping.person_management.repository.AuthUserRepository;

/**
 * TOTP利用者の登録・認証・バックアップコード管理。
 *
 * TOTPシークレットの平文は、このクラスの外へ出さない
 * （登録時にQRを組み立てる場面だけ例外的に返す）。
 */
@Service
public class AuthUserService {

    /** 1利用者あたりに発行するバックアップコードの数 */
    public static final int BACKUP_CODE_COUNT = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** バックアップコードに使う文字。0/O、1/I など紙に印刷して読み間違えやすい文字を除いている */
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final AuthUserRepository userRepository;
    private final AuthBackupCodeRepository backupCodeRepository;
    private final TotpService totpService;
    private final SecretCipher secretCipher;

    public AuthUserService(AuthUserRepository userRepository,
                            AuthBackupCodeRepository backupCodeRepository,
                            TotpService totpService,
                            SecretCipher secretCipher) {
        this.userRepository = userRepository;
        this.backupCodeRepository = backupCodeRepository;
        this.totpService = totpService;
        this.secretCipher = secretCipher;
    }

    // ─── 登録 ──────────────────────────────────────────────

    /** 有効な利用者が1人もいない状態か（初回セットアップ画面を出してよいかの判定に使う） */
    public boolean hasNoActiveUser() {
        return userRepository.countActive() == 0;
    }

    public List<AuthUser> findAll() {
        return userRepository.findAllOrdered();
    }

    public Optional<AuthUser> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username.trim());
    }

    /**
     * 利用者を新規作成し、TOTPシークレットを発行する。
     * この時点では enrolled_at は null（登録途中）。
     * 初回のコード確認が通って初めて completeEnrollment() で有効になる。
     *
     * @return 登録用の情報（平文シークレットとotpauth URIを含む。画面表示にのみ使うこと）
     */
    public EnrollmentInfo createUser(String username, String displayName, String issuer) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("利用者名を入力してください");
        }
        if (userRepository.findByUsername(name).isPresent()) {
            throw new IllegalArgumentException("その利用者名は既に使われています");
        }

        String secret = totpService.generateSecret();

        AuthUser user = new AuthUser();
        user.setUsername(name);
        user.setDisplayName(displayName == null || displayName.isBlank() ? name : displayName.trim());
        user.setTotpSecretEnc(secretCipher.encrypt(secret));
        user.setCreatedAt(LocalDateTime.now());
        AuthUser saved = userRepository.save(user);

        EnrollmentInfo info = new EnrollmentInfo();
        info.userId = saved.getId();
        info.username = saved.getUsername();
        info.secretBase32 = secret;
        info.otpAuthUri = totpService.buildOtpAuthUri(issuer, name, secret);
        return info;
    }

    /**
     * 初回のコード確認。正しければ利用者を有効にし、バックアップコードを発行する。
     *
     * @return 発行したバックアップコードの平文リスト（この1回だけ画面に表示し、以後は二度と取得できない）
     */
    public List<String> completeEnrollment(Long userId, String code) {
        AuthUser user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("利用者が見つかりません"));

        // 既に登録が完了している利用者に対して再度この処理を通してはいけない。
        // 通してしまうと、6桁コードを一度覗き見ただけの相手が
        // バックアップコード10個の再発行を実行でき、恒久的な認証情報を奪える。
        // バックアップコードの再発行は、ログイン後の利用者管理画面からのみ行う。
        if (user.getEnrolledAt() != null) {
            throw new IllegalStateException(
                "この利用者は既に登録済みです。バックアップコードの再発行は利用者管理画面から行ってください");
        }

        String secret = secretCipher.decrypt(user.getTotpSecretEnc());
        if (!totpService.verify(secret, code)) {
            throw new IllegalArgumentException("コードが一致しません。アプリに表示されている6桁を入力してください");
        }

        user.setEnrolledAt(LocalDateTime.now());
        user.setLastTotpStep(currentStep());
        userRepository.save(user);

        return issueBackupCodes(userId);
    }

    // ─── ログイン認証 ──────────────────────────────────────

    /**
     * TOTPコードで認証する。
     *
     * 同じコードの使い回し（盗み見られたコードを30〜90秒以内に再利用される）を防ぐため、
     * 認証が通った時間枠を記録し、それ以前の時間枠のコードは受け付けない。
     */
    public boolean verifyTotp(AuthUser user, String code) {
        if (user == null || !user.isActive()) {
            return false;
        }
        String secret;
        try {
            secret = secretCipher.decrypt(user.getTotpSecretEnc());
        } catch (Exception e) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        if (!totpService.verifyAt(secret, code, now)) {
            return false;
        }
        long step = now / TotpService.PERIOD_SECONDS;
        Long lastStep = user.getLastTotpStep();
        if (lastStep != null && step <= lastStep) {
            // 既に使われた時間枠のコード（使い回し）
            return false;
        }
        user.setLastTotpStep(step);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        return true;
    }

    /**
     * バックアップコードで認証する。一致したコードはその場で使用済みにする。
     */
    public boolean verifyBackupCode(AuthUser user, String code) {
        if (user == null || !user.isActive() || code == null || code.isBlank()) {
            return false;
        }
        String normalized = normalizeBackupCode(code);
        String hash = sha256Hex(normalized);

        for (AuthBackupCode candidate : backupCodeRepository.findByUserId(user.getId())) {
            if (candidate.isUsed()) {
                continue;
            }
            if (constantTimeEquals(candidate.getCodeHash(), hash)) {
                candidate.setUsedAt(LocalDateTime.now());
                backupCodeRepository.save(candidate);
                user.setLastLoginAt(LocalDateTime.now());
                userRepository.save(user);
                return true;
            }
        }
        return false;
    }

    // ─── バックアップコード ────────────────────────────────

    /**
     * バックアップコードを発行し直す（古いコードは無効になる）。
     * @return 平文のコード一覧。呼び出し側で1度だけ表示し、保存しないこと
     */
    public List<String> issueBackupCodes(Long userId) {
        backupCodeRepository.deleteByUserId(userId);

        List<String> plainCodes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String plain = generateBackupCode();
            plainCodes.add(plain);

            AuthBackupCode entity = new AuthBackupCode();
            entity.setUserId(userId);
            entity.setCodeHash(sha256Hex(normalizeBackupCode(plain)));
            entity.setCreatedAt(LocalDateTime.now());
            backupCodeRepository.save(entity);
        }
        return plainCodes;
    }

    public int countUnusedBackupCodes(Long userId) {
        return backupCodeRepository.countUnusedByUserId(userId);
    }

    // ─── 利用者の無効化・再有効化 ──────────────────────────

    /**
     * 利用者を無効にする。
     * 最後の1人を無効にすると誰もログインできなくなるため、それは拒否する。
     */
    public void disableUser(Long userId) {
        AuthUser user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("利用者が見つかりません"));
        if (user.isActive() && userRepository.countActive() <= 1) {
            throw new IllegalStateException(
                "最後の1人は無効にできません。先に別の利用者を登録してください（全員が締め出されるのを防ぐため）");
        }
        user.setDisabledAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void enableUser(Long userId) {
        AuthUser user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("利用者が見つかりません"));
        user.setDisabledAt(null);
        userRepository.save(user);
    }

    // ─── ユーティリティ ────────────────────────────────────

    private static long currentStep() {
        return Instant.now().getEpochSecond() / TotpService.PERIOD_SECONDS;
    }

    /** XXXX-XXXX 形式のバックアップコードを1つ作る */
    private static String generateBackupCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    /** 入力のゆらぎ（小文字・ハイフン・空白）を吸収する */
    static String normalizeBackupCode(String code) {
        return code.trim().toUpperCase(java.util.Locale.ROOT).replace("-", "").replace(" ", "");
    }

    static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("ハッシュの計算に失敗しました", e);
        }
    }

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

    /** 登録用の一時情報。平文シークレットを含むためDBにもログにも残さないこと */
    public static class EnrollmentInfo {
        public Long id;
        public Long userId;
        public String username;
        public String secretBase32;
        public String otpAuthUri;

        public Long getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getSecretBase32() { return secretBase32; }
        public String getOtpAuthUri() { return otpAuthUri; }
    }
}
