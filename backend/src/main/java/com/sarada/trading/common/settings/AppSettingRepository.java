package com.sarada.trading.common.settings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSettingEntity, String> {
}
