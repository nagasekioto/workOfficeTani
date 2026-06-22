package jp.co.housekeeping.person_management.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jp.co.housekeeping.person_management.model.ReceiptsIssued;

@Repository
public interface ReceiptsIssuedRepository extends CrudRepository<ReceiptsIssued, Long> {

    @Query("SELECT * FROM receipts_issued WHERE sales_detail_id = :detailId LIMIT 1")
    Optional<ReceiptsIssued> findBySalesDetailId(@Param("detailId") Long detailId);

    /** 月単位一覧：created_at の年月で絞り込み */
    @Query("SELECT * FROM receipts_issued WHERE TO_CHAR(created_at, 'YYYY-MM') = :month ORDER BY id")
    List<ReceiptsIssued> findByMonth(@Param("month") String month);

    /** 発行済み件数（= 最大 id に相当する採番値を id で代用） */
    @Query("SELECT COALESCE(MAX(id), 0) FROM receipts_issued")
    long findMaxId();
}
