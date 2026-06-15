package jp.co.housekeeping.person_management.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import jp.co.housekeeping.person_management.model.Customer;

@Repository
public interface CustomerRepository extends CrudRepository<Customer, Long> {
}