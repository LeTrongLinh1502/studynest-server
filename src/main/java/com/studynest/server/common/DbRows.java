package com.studynest.server.common;

import com.studynest.server.common.exception.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DbRows {
    private final JdbcTemplate jdbc;

    public DbRows(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> list(String sql, Object... args) {
        return jdbc.queryForList(sql, args).stream().map(DbRows::normalize).toList();
    }

    public Map<String, Object> one(String sql, Object... args) {
        return list(sql, args).stream().findFirst().orElseThrow(() -> ApiException.notFound("Record not found"));
    }

    public Map<String, Object> optional(String sql, Object... args) {
        return list(sql, args).stream().findFirst().orElse(null);
    }

    public int update(String sql, Object... args) { return jdbc.update(sql, args); }

    public long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("Insert returned no key");
        return keys.getKey().longValue();
    }

    public boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private static Map<String, Object> normalize(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (value instanceof Timestamp time) result.put(key, time.toInstant());
            else if ("isCorrect".equals(key) && value instanceof Number number) result.put(key, number.intValue() == 1);
            else result.put(key, value);
        });
        return result;
    }
}
