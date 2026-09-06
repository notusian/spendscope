package com.spendscope.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DashboardWidgetRepository extends JpaRepository<DashboardWidget, Long> {

    List<DashboardWidget> findByUserIdOrderByPositionAscIdAsc(Long userId);

    Optional<DashboardWidget> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}
