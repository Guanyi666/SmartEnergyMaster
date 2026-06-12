package com.smartenergy.backend.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL JSONB 类型处理器。
 *
 * Java 侧以 String（JSON 文本）表示，数据库侧为 jsonb 列。
 * 写入时把 String 包装为 PGobject(type="jsonb")，确保 PG 驱动将参数标注为 jsonb 类型；
 * 读取时从 ResultSet 取出字符串原样返回。
 *
 * 使用方式：在实体的对应字段上加 {@code @TableField(typeHandler = JsonbTypeHandler.class)}
 */
@MappedTypes(String.class)
public class JsonbTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        jsonObject.setValue(parameter);
        ps.setObject(i, jsonObject);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
