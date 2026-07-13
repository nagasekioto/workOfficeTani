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

    @Query("SELECT * FROM receipts_issued WHERE TO_CHAR(created_at, 'YYYY-MM') = :month ORDER BY id")
    List<ReceiptsIssued> findByMonth(@Param("month") String month);

    @Query("SELECT * FROM receipts_issued WHERE person_id = :personId")
    List<ReceiptsIssued> findByPersonId(@Param("personId") Long personId);

    @Query("SELECT * FROM receipts_issued WHERE customer_id = :customerId")
    List<ReceiptsIssued> findByCustomerId(@Param("customerId") Long customerId);
}
