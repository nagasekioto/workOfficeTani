package jp.co.housekeeping.person_management.repository;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jp.co.housekeeping.person_management.model.CustomerRequest;

@Repository
public interface CustomerRequestRepository extends CrudRepository<CustomerRequest, Long> {

    @Query("SELECT * FROM customer_requests WHERE customer_id = :customerId ORDER BY created_at DESC")
    List<CustomerRequest> findByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT * FROM customer_requests ORDER BY created_at DESC")
    List<CustomerRequest> findAllOrdered();
}
