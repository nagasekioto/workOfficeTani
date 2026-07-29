package jp.co.housekeeping.person_management.repository;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jp.co.housekeeping.person_management.model.AuthBackupCode;

@Repository
public interface AuthBackupCodeRepository extends CrudRepository<AuthBackupCode, Long> {

    @Query("SELECT * FROM auth_backup_codes WHERE user_id = :userId ORDER BY id")
    List<AuthBackupCode> findByUserId(@Param("userId") Long userId);

    /** まだ使われていないコードの残数 */
    @Query("SELECT COUNT(*) FROM auth_backup_codes WHERE user_id = :userId AND used_at IS NULL")
    int countUnusedByUserId(@Param("userId") Long userId);

    /** 再発行時に古いコードを消す */
    @Modifying
    @Query("DELETE FROM auth_backup_codes WHERE user_id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
