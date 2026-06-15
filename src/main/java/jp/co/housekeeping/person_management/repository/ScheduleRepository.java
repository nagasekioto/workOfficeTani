package jp.co.housekeeping.person_management.repository;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jp.co.housekeeping.person_management.model.Schedule;

@Repository
public interface ScheduleRepository extends CrudRepository<Schedule, Long> {
    
    @Query("SELECT * FROM schedules WHERE person_id = :personId ORDER BY day_of_week, time_slot")
    List<Schedule> findByPersonId(@Param("personId") Long personId);
}