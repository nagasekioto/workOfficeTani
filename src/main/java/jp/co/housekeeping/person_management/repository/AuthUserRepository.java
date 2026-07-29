package jp.co.housekeeping.person_management.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jp.co.housekeeping.person_management.model.AuthUser;

@Repository
public interface AuthUserRepository extends CrudRepository<AuthUser, Long> {

    @Query("SELECT * FROM auth_users WHERE username = :username")
    Optional<AuthUser> findByUsername(@Param("username") String username);

    @Query("SELECT * FROM auth_users ORDER BY username")
    List<AuthUser> findAllOrdered();

    /** 有効な（登録完了済みかつ無効化されていない）利用者の人数 */
    @Query("SELECT COUNT(*) FROM auth_users WHERE enrolled_at IS NOT NULL AND disabled_at IS NULL")
    int countActive();
}
