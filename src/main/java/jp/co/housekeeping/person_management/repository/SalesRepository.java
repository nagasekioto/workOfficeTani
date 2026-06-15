package jp.co.housekeeping.person_management.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import jp.co.housekeeping.person_management.model.Sales;

@Repository
public interface SalesRepository extends CrudRepository<Sales, Long> {
}