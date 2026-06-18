package jp.co.housekeeping.person_management.repository;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import jp.co.housekeeping.person_management.model.Introduction;

@Repository
public interface IntroductionRepository extends CrudRepository<Introduction, Long> {

    @Query("SELECT COALESCE(MAX(CAST(ref_no AS INTEGER)), 0) FROM introductions WHERE ref_no ~ '^[0-9]+$'")
    int findMaxRefNo();

    @Query("SELECT * FROM introductions ORDER BY created_at DESC")
    Iterable<Introduction> findAllOrderByCreatedAtDesc();
}
