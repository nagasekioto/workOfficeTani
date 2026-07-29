package jp.co.housekeeping.person_management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * SecretCipher（AES-256-GCMによるTOTPシークレットの暗号化）のテスト。
 */
class SecretCipherTest {

    @Test
    void 暗号化して復号すると元の文字列に戻る() {
        SecretCipher cipher = new SecretCipher("test-encryption-key-1");
        String plain = "ABCDEFGHIJKLMNOPQRST";

        String encrypted = cipher.encrypt(plain);
        String decrypted = cipher.decrypt(encrypted);

        assertEquals(plain, decrypted);
    }

    @Test
    void 同じ平文を2回暗号化すると異なる暗号文になる() {
        SecretCipher cipher = new SecretCipher("test-encryption-key-1");
        String plain = "ABCDEFGHIJKLMNOPQRST";

        String encrypted1 = cipher.encrypt(plain);
        String encrypted2 = cipher.encrypt(plain);

        assertNotEquals(encrypted1, encrypted2, "IVがランダムなら暗号文は毎回異なるはず");
        // ただし、どちらも正しく復号できること
        assertEquals(plain, cipher.decrypt(encrypted1));
        assertEquals(plain, cipher.decrypt(encrypted2));
    }

    @Test
    void 異なる鍵で作ったSecretCipherでは復号に失敗して例外になる() {
        SecretCipher cipherA = new SecretCipher("key-A");
        SecretCipher cipherB = new SecretCipher("key-B");

        String encrypted = cipherA.encrypt("secret-value");

        assertThrows(IllegalStateException.class, () -> cipherB.decrypt(encrypted));
    }

    @Test
    void 鍵未設定の場合isKeyConfiguredがfalseでencryptは例外になる() {
        SecretCipher cipher = new SecretCipher("");

        assertFalse(cipher.isKeyConfigured());
        assertThrows(IllegalStateException.class, () -> cipher.encrypt("something"));
    }

    @Test
    void 鍵が設定されている場合isKeyConfiguredがtrueになる() {
        SecretCipher cipher = new SecretCipher("configured-key");
        assertTrue(cipher.isKeyConfigured());
    }
}
