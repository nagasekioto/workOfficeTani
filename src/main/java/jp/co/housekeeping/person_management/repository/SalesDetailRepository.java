package jp.co.housekeeping.person_management.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jp.co.housekeeping.person_management.model.SalesDetail;

@Repository
public interface SalesDetailRepository extends CrudRepository<SalesDetail, Long> {

    @Query("SELECT * FROM sales_details WHERE sales_id = :salesId ORDER BY COALESCE(detail_order, 0), id")
    List<SalesDetail> findBySalesId(@Param("salesId") Long salesId);

    @Query("SELECT * FROM sales_details WHERE introduction_date >= :startDate AND introduction_date <= :endDate ORDER BY introduction_date, id")
    List<SalesDetail> findByIntroductionDateBetween(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    @Query("SELECT * FROM sales_details WHERE work_start_date >= :startDate AND work_start_date <= :endDate ORDER BY work_start_date, id")
    List<SalesDetail> findByWorkStartDateBetween(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    @Query("SELECT COALESCE(MAX(CAST(receipt_no AS INTEGER)), 0) FROM sales_details WHERE receipt_no ~ '^[0-9]+$'")
    int findMaxReceiptNo();
}
