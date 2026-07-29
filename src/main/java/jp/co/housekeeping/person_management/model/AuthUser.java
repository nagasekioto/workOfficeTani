package jp.co.housekeeping.person_management.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * TOTP（Google Authenticator）でログインする利用者テーブル (auth_users)
 *
 * totpSecretEnc は AES-GCM で暗号化された状態でのみ保持する。
 * 平文のシークレットをこのクラスに持たせないこと（ログやエラー画面に出る事故を防ぐため）。
 */
@Table("auth_users")
public class AuthUser {

    @Id
    private Long id;

    private String username;        // ログイン時に入力する利用者名
    private String displayName;     // 画面表示用の氏名
    private String totpSecretEnc;   // 暗号化済みTOTPシークレット（復号は SecretCipher 経由）
    private Long lastTotpStep;      // 最後に認証が通った時間枠。同じコードの使い回しを防ぐ
    private LocalDateTime enrolledAt;   // 初回のコード確認が通った日時。nullなら登録途中
    private LocalDateTime disabledAt;   // 値が入っていれば無効
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    public AuthUser() {}

    /** 有効な利用者か（登録完了済み かつ 無効化されていない） */
    public boolean isActive() {
        return enrolledAt != null && disabledAt == null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getTotpSecretEnc() { return totpSecretEnc; }
    public void setTotpSecretEnc(String totpSecretEnc) { this.totpSecretEnc = totpSecretEnc; }
    public Long getLastTotpStep() { return lastTotpStep; }
    public void setLastTotpStep(Long lastTotpStep) { this.lastTotpStep = lastTotpStep; }
    public LocalDateTime getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(LocalDateTime enrolledAt) { this.enrolledAt = enrolledAt; }
    public LocalDateTime getDisabledAt() { return disabledAt; }
    public void setDisabledAt(LocalDateTime disabledAt) { this.disabledAt = disabledAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
