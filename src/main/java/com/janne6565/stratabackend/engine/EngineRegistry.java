package com.janne6565.stratabackend.engine;

import com.janne6565.stratabackend.common.BadRequestException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Resolves the {@link DatabaseEngine} for a datasource's driver. */
@Component
public class EngineRegistry {

    private final Map<String, DatabaseEngine> byDriver;

    public EngineRegistry(List<DatabaseEngine> engines) {
        this.byDriver =
                engines.stream()
                        .collect(Collectors.toMap(DatabaseEngine::driver, Function.identity()));
    }

    public DatabaseEngine forDriver(String driver) {
        DatabaseEngine engine = byDriver.get(driver);
        if (engine == null) {
            throw new BadRequestException("No engine adapter for driver: " + driver);
        }
        return engine;
    }

    public boolean supports(String driver) {
        return byDriver.containsKey(driver);
    }
}
