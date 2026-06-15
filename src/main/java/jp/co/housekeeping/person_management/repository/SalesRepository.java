package jp.co.housekeeping.person_management.repository;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jp.co.housekeeping.person_management.model.Sales;

@Repository
public interface SalesRepository extends CrudRepository<Sales, Long> {

    @Query("SELECT * FROM sales WHERE person_id = :personId ORDER BY id")
    List<Sales> findByPersonId(@Param("personId") Long personId);
}
