package jp.co.housekeeping.person_management.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * バックアップコードテーブル (auth_backup_codes)
 *
 * 認証アプリを入れた端末を紛失した場合に、代わりに1回だけ使えるコード。
 * 平文は発行直後に1度だけ画面表示し、DBにはSHA-256ハッシュのみを保存する。
 */
@Table("auth_backup_codes")
public class AuthBackupCode {

    @Id
    private Long id;

    private Long userId;
    private String codeHash;         // SHA-256のhex文字列。平文は保存しない
    private LocalDateTime usedAt;    // 1回使ったら日時が入り、以降は使えない
    private LocalDateTime createdAt;

    public AuthBackupCode() {}

    public boolean isUsed() {
        return usedAt != null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
