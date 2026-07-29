package jp.co.housekeeping.person_management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import jp.co.housekeeping.person_management.model.AuthBackupCode;
import jp.co.housekeeping.person_management.model.AuthUser;
import jp.co.housekeeping.person_management.repository.AuthBackupCodeRepository;
import jp.co.housekeeping.person_management.repository.AuthUserRepository;

/**
 * AuthUserService（TOTP利用者の登録・認証・バックアップコード管理）のテスト。
 * リポジトリはMockitoでモックし、TotpService/SecretCipherは実物（決定的で軽量なため）を使う。
 */
class AuthUserServiceTest {

    /**
     * 登録済みの利用者に対して completeEnrollment を通してはいけない。
     * 通すとバックアップコードが再発行され、6桁コードを一度覗き見ただけの相手が
     * 恒久的に使える認証情報10個を奪えてしまう。
     */
    @org.junit.jupiter.api.Test
    void 登録済みの利用者に対するcompleteEnrollmentは拒否される() {
        jp.co.housekeeping.person_management.model.AuthUser user =
                new jp.co.housekeeping.person_management.model.AuthUser();
        user.setId(1L);
        user.setUsername("tani");
        user.setEnrolledAt(java.time.LocalDateTime.now()); // 既に登録完了済み

        org.mockito.Mockito.when(userRepository.findById(1L))
                .thenReturn(java.util.Optional.of(user));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.completeEnrollment(1L, "123456"));

        // バックアップコードが削除・再発行されていないこと
        org.mockito.Mockito.verify(backupCodeRepository, org.mockito.Mockito.never())
                .deleteByUserId(org.mockito.ArgumentMatchers.anyLong());
    }

    private AuthUserRepository userRepository;
    private AuthBackupCodeRepository backupCodeRepository;
    private TotpService totpService;
    private SecretCipher secretCipher;
    private AuthUserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(AuthUserRepository.class);
        backupCodeRepository = mock(AuthBackupCodeRepository.class);
        totpService = new TotpService();
        secretCipher = new SecretCipher("unit-test-encryption-key");
        service = new AuthUserService(userRepository, backupCodeRepository, totpService, secretCipher);

        when(userRepository.save(any(AuthUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(backupCodeRepository.save(any(AuthBackupCode.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private AuthUser buildUser(Long id, String secretBase32) {
        AuthUser user = new AuthUser();
        user.setId(id);
        user.setUsername("tani");
        user.setDisplayName("谷");
        user.setTotpSecretEnc(secretCipher.encrypt(secretBase32));
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    // ─── completeEnrollment ─────────────────────────────────

    @Test
    void completeEnrollmentは正しいコードでenrolledAtを設定しバックアップコードを10個返す() {
        String secret = totpService.generateSecret();
        AuthUser user = buildUser(1L, secret);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(backupCodeRepository.findByUserId(1L)).thenReturn(new ArrayList<>());

        long step = Instant.now().getEpochSecond() / TotpService.PERIOD_SECONDS;
        String code = totpService.generateCode(TotpService.fromBase32(secret), step);

        List<String> backupCodes = service.completeEnrollment(1L, code);

        assertNotNull(user.getEnrolledAt());
        assertEquals(AuthUserService.BACKUP_CODE_COUNT, backupCodes.size());
        assertEquals(10, backupCodes.size());
    }

    @Test
    void completeEnrollmentはコードが誤っているとIllegalArgumentExceptionを投げる() {
        String secret = totpService.generateSecret();
        AuthUser user = buildUser(1L, secret);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> service.completeEnrollment(1L, "000000"));
        assertEquals(null, user.getEnrolledAt());
    }

    // ─── issueBackupCodes：平文をDBに保存していないことの確認 ───

    @Test
    void issueBackupCodesが返す平文コードは保存されたcodeHashと一致しない() {
        ArgumentCaptor<AuthBackupCode> captor = ArgumentCaptor.forClass(AuthBackupCode.class);
        when(backupCodeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        List<String> plainCodes = service.issueBackupCodes(5L);

        List<AuthBackupCode> saved = captor.getAllValues();
        assertEquals(AuthUserService.BACKUP_CODE_COUNT, saved.size());
        assertEquals(AuthUserService.BACKUP_CODE_COUNT, plainCodes.size());

        for (int i = 0; i < plainCodes.size(); i++) {
            String plain = plainCodes.get(i);
            String hash = saved.get(i).getCodeHash();
            assertNotEquals(plain, hash, "平文のバックアップコードがそのままDBに保存されている");
            assertEquals(AuthUserService.sha256Hex(AuthUserService.normalizeBackupCode(plain)), hash,
                "保存されたハッシュが、平文から計算した値と一致しない");
        }
    }

    // ─── verifyBackupCode：使い切りの確認 ─────────────────────

    @Test
    void verifyBackupCodeは正しいコードを1回だけ受け付け2回目は拒否する() {
        AuthUser user = new AuthUser();
        user.setId(2L);
        user.setEnrolledAt(LocalDateTime.now()); // active(enrolledAt!=null, disabledAt==null)

        String plainCode = "ABCD-1234";
        String hash = AuthUserService.sha256Hex(AuthUserService.normalizeBackupCode(plainCode));
        AuthBackupCode stored = new AuthBackupCode();
        stored.setId(10L);
        stored.setUserId(2L);
        stored.setCodeHash(hash);

        List<AuthBackupCode> codes = new ArrayList<>();
        codes.add(stored);
        when(backupCodeRepository.findByUserId(2L)).thenReturn(codes);

        assertTrue(service.verifyBackupCode(user, plainCode), "初回は受け付けられるはず");
        assertNotNull(stored.getUsedAt(), "使用済みとしてマークされているはず");

        assertFalse(service.verifyBackupCode(user, plainCode), "2回目は拒否されるはず");
    }

    // ─── verifyTotp：使い回し防止 ─────────────────────────────

    @Test
    void verifyTotpは同じ時間枠のコードの使い回しを拒否する() {
        String secret = totpService.generateSecret();
        AuthUser user = buildUser(3L, secret);
        user.setEnrolledAt(LocalDateTime.now()); // active

        long step = Instant.now().getEpochSecond() / TotpService.PERIOD_SECONDS;
        String code = totpService.generateCode(TotpService.fromBase32(secret), step);

        assertTrue(service.verifyTotp(user, code), "初回は受け付けられるはず");
        assertFalse(service.verifyTotp(user, code), "同じ時間枠のコードの再利用は拒否されるはず");
    }

    // ─── disableUser：最後の1人の保護 ─────────────────────────

    @Test
    void disableUserは有効な利用者が1人しかいないときIllegalStateExceptionを投げる() {
        AuthUser user = new AuthUser();
        user.setId(4L);
        user.setEnrolledAt(LocalDateTime.now()); // active

        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(userRepository.countActive()).thenReturn(1);

        assertThrows(IllegalStateException.class, () -> service.disableUser(4L));
    }

    @Test
    void disableUserは他に有効な利用者がいれば無効化できる() {
        AuthUser user = new AuthUser();
        user.setId(4L);
        user.setEnrolledAt(LocalDateTime.now());

        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(userRepository.countActive()).thenReturn(2);

        service.disableUser(4L);

        assertNotNull(user.getDisabledAt());
    }

    // ─── normalizeBackupCode ────────────────────────────────

    @Test
    void normalizeBackupCodeは小文字ハイフン空白を吸収する() {
        assertEquals("ABCD1234", AuthUserService.normalizeBackupCode("abcd-1234"));
        assertEquals("ABCD1234", AuthUserService.normalizeBackupCode(" ABCD-1234 "));
        assertEquals("ABCD1234", AuthUserService.normalizeBackupCode("ab cd12 34"));
    }
}
