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

    @Query("SELECT * FROM sales_details WHERE person_id_ref = :personId ORDER BY introduction_date")
    List<SalesDetail> findByPersonId(@Param("personId") Long personId);

    @Query("SELECT COUNT(*) FROM sales_details sd JOIN sales s ON sd.sales_id = s.id WHERE s.person_id = :personId AND sd.customer_id = :customerId")
    int countByPersonAndCustomer(@Param("personId") Long personId, @Param("customerId") Long customerId);

    @Query("SELECT COUNT(*) FROM sales_details WHERE customer_id = :customerId")
    int countByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT COALESCE(MAX(CAST(receipt_no AS INTEGER)), 0) FROM sales_details WHERE receipt_no ~ '^[0-9]+$'")
    int findMaxReceiptNo();

    @Query("SELECT sd.* FROM sales_details sd JOIN sales s ON sd.sales_id = s.id WHERE s.person_id = :personId ORDER BY sd.introduction_date")
    List<SalesDetail> findByPersonIdViaJoin(@Param("personId") Long personId);
}
