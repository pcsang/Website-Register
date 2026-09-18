package com.register.backend.repository;

import com.register.backend.entity.DashboardSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardSettingsRepository extends JpaRepository<DashboardSettings, Long> {
}
