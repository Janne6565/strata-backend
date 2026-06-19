package com.janne6565.stratabackend.engine.redis;

import com.janne6565.stratabackend.common.EngineException;
import com.janne6565.stratabackend.engine.ColumnInfo;
import com.janne6565.stratabackend.engine.ConnectionDetails;
import com.janne6565.stratabackend.engine.DatabaseEngine;
import com.janne6565.stratabackend.engine.ObjectRef;
import com.janne6565.stratabackend.engine.QueryMode;
import com.janne6565.stratabackend.engine.QueryResult;
import com.janne6565.stratabackend.engine.RowPage;
import com.janne6565.stratabackend.engine.SchemaInfo;
import com.janne6565.stratabackend.engine.TableInfo;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.NestedMultiOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.ProtocolKeyword;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Redis adapter (ARCHITECTURE.md §9). The keyspace maps onto the table abstraction: introspection
 * and browse expose keys with their type and TTL, and runQuery executes a single Redis command
 * supplied as a command line. In {@link QueryMode#READ} only a whitelist of non-mutating commands
 * is permitted (defence-in-depth, AUTH.md). Connections are cached per endpoint and are long-lived
 * (Lettuce connections are thread-safe); resolved credentials live only in the connection URI.
 */
@Component
public class RedisEngine implements DatabaseEngine {

    private static final int MAX_QUERY_ROWS = 1000;
    private static final int SCAN_BATCH = 256;
    private static final String KEYS_OBJECT = "keys";

    /** Non-mutating commands permitted in read-only mode (lower-cased). */
    private static final Set<String> READ_COMMANDS =
            Set.of(
                    "get", "mget", "strlen", "getrange", "exists", "type", "ttl", "pttl", "keys",
                    "scan", "hget", "hgetall", "hkeys", "hvals", "hlen", "hmget", "lrange", "llen",
                    "lindex", "smembers", "scard", "sismember", "zrange", "zcard", "zscore",
                    "dbsize", "randomkey", "object", "memory");

    private final Map<String, RedisClient> clients = new ConcurrentHashMap<>();
    private final Map<String, StatefulRedisConnection<String, String>> connections =
            new ConcurrentHashMap<>();

    @Override
    public String driver() {
        return "redis";
    }

    @Override
    public boolean canEnforceReadOnly() {
        return true;
    }

    @Override
    public SchemaInfo introspect(ConnectionDetails details) {
        // Redis is schemaless: surface one synthetic object describing the keyspace shape.
        List<ColumnInfo> columns =
                List.of(
                        new ColumnInfo("key", "string", false, true),
                        new ColumnInfo("type", "string", false, false),
                        new ColumnInfo("ttl", "long", true, false));
        String schema = "db" + dbIndex(details.database());
        try {
            commands(details).ping();
        } catch (RuntimeException ex) {
            throw new EngineException("Introspection failed: " + ex.getMessage());
        }
        return new SchemaInfo(List.of(new TableInfo(schema, KEYS_OBJECT, "KEYSPACE", columns)));
    }

    @Override
    public RowPage browse(ConnectionDetails details, ObjectRef ref, int offset, int limit) {
        try {
            RedisCommands<String, String> sync = commands(details);
            List<String> keys = scanKeys(sync, Math.max(0, offset) + Math.max(0, limit));
            keys.sort(String::compareTo);
            List<List<Object>> rows = new ArrayList<>();
            int from = Math.min(Math.max(0, offset), keys.size());
            int to = Math.min(from + Math.max(0, limit), keys.size());
            for (String key : keys.subList(from, to)) {
                rows.add(List.of(key, sync.type(key), sync.ttl(key)));
            }
            return new RowPage(List.of("key", "type", "ttl"), rows, offset, limit);
        } catch (RuntimeException ex) {
            throw new EngineException("Browse failed: " + ex.getMessage());
        }
    }

    @Override
    public QueryResult runQuery(ConnectionDetails details, String query, QueryMode mode) {
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            throw new EngineException("Empty command");
        }
        String name = tokens.get(0);
        if (mode == QueryMode.READ && !READ_COMMANDS.contains(name.toLowerCase())) {
            throw new EngineException("Command '" + name + "' is not permitted in read-only mode");
        }
        try {
            RedisCommands<String, String> sync = commands(details);
            CommandArgs<String, String> args = new CommandArgs<>(StringCodec.UTF8);
            for (int i = 1; i < tokens.size(); i++) {
                args.add(tokens.get(i));
            }
            List<Object> reply = sync.dispatch(keyword(name), new NestedMultiOutput<>(StringCodec.UTF8), args);
            return render(reply);
        } catch (EngineException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new EngineException("Query failed: " + ex.getMessage());
        }
    }

    private QueryResult render(List<Object> reply) {
        List<List<Object>> rows = new ArrayList<>();
        if (reply != null) {
            for (Object element : reply) {
                if (rows.size() >= MAX_QUERY_ROWS) {
                    break;
                }
                rows.add(List.of(stringify(element)));
            }
        }
        return QueryResult.ofRows(List.of("value"), rows);
    }

    private String stringify(Object value) {
        return value == null ? "" : value.toString();
    }

    private List<String> scanKeys(RedisCommands<String, String> sync, int needed) {
        List<String> keys = new ArrayList<>();
        ScanCursor cursor = ScanCursor.INITIAL;
        do {
            KeyScanCursor<String> result = sync.scan(cursor, ScanArgs.Builder.limit(SCAN_BATCH));
            keys.addAll(result.getKeys());
            cursor = result;
        } while (!cursor.isFinished() && keys.size() < Math.max(needed, SCAN_BATCH));
        return keys;
    }

    private ProtocolKeyword keyword(String name) {
        try {
            return CommandType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new EngineException("Unsupported Redis command: " + name);
        }
    }

    private List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        for (String token : query.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private RedisCommands<String, String> commands(ConnectionDetails details) {
        String key = key(details);
        return connections
                .computeIfAbsent(
                        key,
                        k -> {
                            RedisClient client = RedisClient.create(redisUri(details));
                            clients.put(k, client);
                            return client.connect();
                        })
                .sync();
    }

    private String key(ConnectionDetails details) {
        return details.host()
                + ":"
                + details.port()
                + ":"
                + dbIndex(details.database())
                + ":"
                + details.username();
    }

    private RedisURI redisUri(ConnectionDetails details) {
        RedisURI.Builder builder =
                RedisURI.builder().withHost(details.host()).withPort(details.port());
        boolean hasPassword = details.password() != null && !details.password().isBlank();
        if (details.username() != null && !details.username().isBlank()) {
            builder.withAuthentication(
                    details.username(), hasPassword ? details.password().toCharArray() : new char[0]);
        } else if (hasPassword) {
            builder.withPassword(details.password().toCharArray());
        }
        return builder.withDatabase(dbIndex(details.database())).build();
    }

    private int dbIndex(String database) {
        if (database == null || database.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(database.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @PreDestroy
    void closeAll() {
        connections.values().forEach(StatefulRedisConnection::close);
        clients.values().forEach(RedisClient::shutdown);
        connections.clear();
        clients.clear();
    }
}
