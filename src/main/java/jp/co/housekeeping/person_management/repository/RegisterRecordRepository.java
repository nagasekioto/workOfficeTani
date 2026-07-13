package jp.co.housekeeping.person_management.repository;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jp.co.housekeeping.person_management.model.RegisterRecord;

@Repository
public interface RegisterRecordRepository extends CrudRepository<RegisterRecord, Long> {

    @Query("SELECT * FROM register_records WHERE work_month = :workMonth ORDER BY created_at DESC")
    List<RegisterRecord> findByWorkMonth(@Param("workMonth") String workMonth);

    @Query("SELECT * FROM register_records WHERE work_month LIKE :yearPrefix ORDER BY work_month, created_at")
    List<RegisterRecord> findByYear(@Param("yearPrefix") String yearPrefix);

    @Query("SELECT * FROM register_records WHERE person_id = :personId")
    List<RegisterRecord> findByPersonId(@Param("personId") Long personId);
}
