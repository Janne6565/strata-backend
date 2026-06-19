package com.janne6565.stratabackend.services.engine.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.janne6565.stratabackend.model.core.ColumnInfo;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.ObjectRef;
import com.janne6565.stratabackend.model.core.QueryMode;
import com.janne6565.stratabackend.model.core.QueryResult;
import com.janne6565.stratabackend.model.core.RowPage;
import com.janne6565.stratabackend.model.core.SchemaInfo;
import com.janne6565.stratabackend.model.core.TableInfo;
import com.janne6565.stratabackend.model.exception.EngineException;
import com.janne6565.stratabackend.services.engine.DatabaseEngine;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * InfluxDB 2.x adapter (ARCHITECTURE.md §9). InfluxDB authenticates with a token against an org and
 * bucket rather than user/password/database, so the {@link ConnectionDetails} fields are mapped:
 * username&rarr;org, password&rarr;token, database&rarr;bucket. Measurements map onto the table
 * abstraction: introspect lists measurements and their field keys, browse pages a measurement's
 * points. {@link QueryMode#READ} runs a Flux query and rejects the {@code to()} write function;
 * {@link QueryMode#WRITE} ingests line protocol. Clients are cached per endpoint; the token lives
 * only in the client, in memory (AUTH.md).
 */
@Component
public class InfluxEngine implements DatabaseEngine {

    private static final int MAX_QUERY_ROWS = 1000;

    private final Map<String, InfluxDBClient> clients = new ConcurrentHashMap<>();

    @Override
    public String driver() {
        return "influxdb";
    }

    @Override
    public boolean canEnforceReadOnly() {
        return true;
    }

    @Override
    public SchemaInfo introspect(ConnectionDetails details) {
        String bucket = bucket(details);
        try {
            InfluxDBClient client = client(details);
            List<TableInfo> tables = new ArrayList<>();
            String measurementsFlux =
                    "import \"influxdata/influxdb/schema\"\n"
                            + "schema.measurements(bucket: \""
                            + bucket
                            + "\")";
            for (String measurement : values(client, details, measurementsFlux)) {
                tables.add(
                        new TableInfo(
                                bucket,
                                measurement,
                                "MEASUREMENT",
                                fieldColumns(client, details, bucket, measurement)));
            }
            return new SchemaInfo(tables);
        } catch (RuntimeException ex) {
            throw new EngineException("Introspection failed: " + ex.getMessage());
        }
    }

    @Override
    public RowPage browse(ConnectionDetails details, ObjectRef ref, int offset, int limit) {
        String flux =
                "from(bucket: \""
                        + bucket(details)
                        + "\")\n"
                        + "  |> range(start: 0)\n"
                        + "  |> filter(fn: (r) => r._measurement == \""
                        + ref.name()
                        + "\")\n"
                        + "  |> limit(n: "
                        + Math.max(0, limit)
                        + ", offset: "
                        + Math.max(0, offset)
                        + ")";
        try {
            List<FluxRecord> records = records(client(details), details, flux);
            List<String> columns = unionKeys(records);
            return new RowPage(columns, toRows(records, columns), offset, limit);
        } catch (RuntimeException ex) {
            throw new EngineException("Browse failed: " + ex.getMessage());
        }
    }

    @Override
    public QueryResult runQuery(ConnectionDetails details, String query, QueryMode mode) {
        if (mode == QueryMode.WRITE) {
            return writeLineProtocol(details, query);
        }
        if (query.contains("to(")) {
            throw new EngineException("The to() write function is not permitted in read-only mode");
        }
        try {
            List<FluxRecord> records = records(client(details), details, query);
            List<String> columns = unionKeys(records);
            return QueryResult.ofRows(columns, toRows(records, columns));
        } catch (RuntimeException ex) {
            throw new EngineException("Query failed: " + ex.getMessage());
        }
    }

    private QueryResult writeLineProtocol(ConnectionDetails details, String data) {
        List<String> lines = new ArrayList<>();
        for (String line : data.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        if (lines.isEmpty()) {
            throw new EngineException("No line-protocol records to write");
        }
        try {
            WriteApiBlocking write = client(details).getWriteApiBlocking();
            write.writeRecords(WritePrecision.NS, lines);
            return QueryResult.ofUpdate(lines.size());
        } catch (RuntimeException ex) {
            throw new EngineException("Write failed: " + ex.getMessage());
        }
    }

    private List<ColumnInfo> fieldColumns(
            InfluxDBClient client, ConnectionDetails details, String bucket, String measurement) {
        String flux =
                "import \"influxdata/influxdb/schema\"\n"
                        + "schema.measurementFieldKeys(bucket: \""
                        + bucket
                        + "\", measurement: \""
                        + measurement
                        + "\")";
        List<ColumnInfo> columns = new ArrayList<>();
        columns.add(new ColumnInfo("_time", "time", false, false));
        for (String field : values(client, details, flux)) {
            columns.add(new ColumnInfo(field, "field", true, false));
        }
        return columns;
    }

    private List<String> values(InfluxDBClient client, ConnectionDetails details, String flux) {
        List<String> values = new ArrayList<>();
        for (FluxRecord record : records(client, details, flux)) {
            Object value = record.getValue();
            if (value != null) {
                values.add(value.toString());
            }
        }
        return values;
    }

    private List<FluxRecord> records(
            InfluxDBClient client, ConnectionDetails details, String flux) {
        List<FluxRecord> records = new ArrayList<>();
        for (FluxTable table : client.getQueryApi().query(flux, org(details))) {
            records.addAll(table.getRecords());
        }
        return records;
    }

    private List<String> unionKeys(List<FluxRecord> records) {
        Set<String> keys = new LinkedHashSet<>();
        for (FluxRecord record : records) {
            keys.addAll(record.getValues().keySet());
        }
        return new ArrayList<>(keys);
    }

    private List<List<Object>> toRows(List<FluxRecord> records, List<String> columns) {
        List<List<Object>> rows = new ArrayList<>();
        for (FluxRecord record : records) {
            if (rows.size() >= MAX_QUERY_ROWS) {
                break;
            }
            Map<String, Object> values = record.getValues();
            List<Object> row = new ArrayList<>(columns.size());
            for (String column : columns) {
                row.add(cell(values.get(column)));
            }
            rows.add(row);
        }
        return rows;
    }

    private Object cell(Object value) {
        if (value == null
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof String) {
            return value;
        }
        return value.toString();
    }

    private InfluxDBClient client(ConnectionDetails details) {
        return clients.computeIfAbsent(
                key(details),
                k ->
                        InfluxDBClientFactory.create(
                                url(details),
                                token(details).toCharArray(),
                                org(details),
                                bucket(details)));
    }

    private String key(ConnectionDetails details) {
        return details.host() + ":" + details.port() + ":" + org(details) + ":" + bucket(details);
    }

    private String url(ConnectionDetails details) {
        return "http://" + details.host() + ":" + details.port();
    }

    private String org(ConnectionDetails details) {
        return details.username();
    }

    private String bucket(ConnectionDetails details) {
        return details.database();
    }

    private String token(ConnectionDetails details) {
        return details.password() == null ? "" : details.password();
    }

    @PreDestroy
    void closeAll() {
        clients.values().forEach(InfluxDBClient::close);
        clients.clear();
    }
}
