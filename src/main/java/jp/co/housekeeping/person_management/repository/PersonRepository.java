package jp.co.housekeeping.person_management.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import jp.co.housekeeping.person_management.model.Person;

/**
 * personsテーブルへのCRUD操作を提供するリポジトリ
 */
@Repository
public interface PersonRepository extends CrudRepository<Person, Long> {
    // 基本的なCRUD操作（findAll, save, deleteなど）は自動で使える
}