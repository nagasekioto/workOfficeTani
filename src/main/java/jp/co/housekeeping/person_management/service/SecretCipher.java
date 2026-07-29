package jp.co.housekeeping.person_management.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * TOTPシークレットを暗号化・復号するためのサービス（AES-256-GCM）。
 *
 * 【なぜ暗号化するのか】
 * TOTPシークレットを個人情報と同じDBに平文で置くと、DBを丸ごと抜かれた時点で
 * 第2要素も一緒に奪われ、二要素にした意味が消える。
 * そこで暗号化キーだけはDBの外（環境変数 TOTP_ENCRYPTION_KEY）に置き、
 * DBのダンプだけでは復号できないようにしている。
 * これは「唯一の命綱を、守るべき対象と同じ場所に置かない」という方針の適用。
 *
 * 【キーを失った場合】
 * すべての利用者のTOTPシークレットが復号できなくなる。
 * その場合はバックアップコード（ハッシュ保管のため暗号化キーとは無関係に使える）で
 * ログインし、TOTPを登録し直すこと。手順は docs/INCIDENT_RESPONSE.md を参照。
 * 暗号化キーは必ず、このシステムとは別の場所（紙・別クラウド）にも保管すること。
 */
@Service
public class SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12; // GCMの推奨値

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;
    private final boolean keyConfigured;

    public SecretCipher(@Value("${app.auth.totp-encryption-key:}") String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            this.key = null;
            this.keyConfigured = false;
        } else {
            // 任意の長さの文字列を、AES-256が要求する32バイトの鍵に変換する
            this.key = new SecretKeySpec(sha256(configuredKey), "AES");
            this.keyConfigured = true;
        }
    }

    /**
     * 暗号化キーが設定されているか。
     * false の場合、TOTPの登録・ログインは行えない（設定漏れに気付けるようにするため、
     * 平文で保存するフォールバックは意図的に用意していない）。
     */
    public boolean isKeyConfigured() {
        return keyConfigured;
    }

    /** 平文を暗号化し、「IV + 暗号文」をBase64にして返す */
    public String encrypt(String plainText) {
        requireKey();
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 復号時にIVが必要なので、暗号文の先頭にIVを連結して保存する
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("暗号化に失敗しました", e);
        }
    }

    /** encrypt() で作った文字列を復号する */
    public String decrypt(String encoded) {
        requireKey();
        if (encoded == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length <= IV_BYTES) {
                throw new IllegalArgumentException("暗号文が短すぎます");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            System.arraycopy(combined, IV_BYTES, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 鍵が違う場合もここに来る（GCMは改ざん・鍵違いを検出できる）
            throw new IllegalStateException("復号に失敗しました。暗号化キーが変更された可能性があります", e);
        }
    }

    private void requireKey() {
        if (!keyConfigured) {
            throw new IllegalStateException(
                "TOTPの暗号化キーが設定されていません。環境変数 TOTP_ENCRYPTION_KEY を設定してください");
        }
    }

    private static byte[] sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("鍵の生成に失敗しました", e);
        }
    }
}
