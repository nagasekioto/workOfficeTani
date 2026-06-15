package jp.co.housekeeping.person_management.repository;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jp.co.housekeeping.person_management.model.SalesDetail;

@Repository
public interface SalesDetailRepository extends CrudRepository<SalesDetail, Long> {
    
    @Query("SELECT * FROM sales_details WHERE sales_id = :salesId ORDER BY detail_order")
    List<SalesDetail> findBySalesId(@Param("salesId") Long salesId);
}