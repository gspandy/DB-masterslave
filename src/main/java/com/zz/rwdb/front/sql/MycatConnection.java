package com.zz.rwdb.front.sql;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import com.zz.rwdb.front.sql.error.SQLError;
import com.zz.rwdb.util.Constant;

public class MycatConnection implements Connection {

    private static ThreadLocal<Connection> local = new ThreadLocal<Connection>();
    private volatile Connection realConn;

    private String catalog;
    private String schema;

    private volatile boolean autoCommit = true;
    private volatile boolean closed;
    private volatile boolean readOnly;

    private int transactionIsolation;
    private int holdability;

    private Map<String, Class<?>> typeMap = null;

    public MycatConnection() {
    }

    public void release() {
        this.catalog = null;
        this.schema = null;
        this.autoCommit = true;
        this.closed = false;
        this.readOnly = false;
        this.transactionIsolation = 0;
        this.holdability = 0;
        this.typeMap = null;
        this.realConn = null;
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return new MycatPreparedStatement(sql, this);

    }

    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return new MycatPreparedStatement(sql, this, autoGeneratedKeys);
    }

    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return new MycatPreparedStatement(sql, this, columnIndexes);
    }

    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return new MycatPreparedStatement(sql, this, columnNames);
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        return new MycatPreparedStatement(sql, this, resultSetType, resultSetConcurrency);
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
            int resultSetHoldability) throws SQLException {
        return new MycatPreparedStatement(sql, this, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        this.autoCommit = autoCommit;
    }

    @Override
    public void commit() throws SQLException {
        /*
         * if (realConn != null) { realConn.commit(); }
         */

        if (local.get() != null) {
            Connection c = local.get();
            /*if (realConn.equals(c)) {
                System.out.println("oooooooooooook");
            } else {
                System.out.println("not oooook");
            }*/
            c.commit();
        }
    }

    @Override
    public void rollback() throws SQLException {
        if (realConn != null) {
            realConn.rollback();
        }
    }

    @Override
    public void close() throws SQLException {
        this.closed = true;
        if (realConn != null) {
            realConn.close();
        }
        if (local.get() != null) {
            local.get().close();
            local.remove();
        }
        release();
    }

    private void initRealConnection(Connection con) throws SQLException {
        if (this.transactionIsolation != 0) {
            con.setTransactionIsolation(this.transactionIsolation);
        }
        if (this.getHoldability() != 0) {
            con.setHoldability(this.holdability);
        }
        if (!this.autoCommit) {
            con.setAutoCommit(this.autoCommit);
        }
        if (this.catalog != null) {
            con.setCatalog(this.catalog);
        }
        if (typeMap != null) {
            con.setTypeMap(typeMap);
        }
        if (this.readOnly) {
            con.setReadOnly(this.readOnly);
        }
        if (this.schema != null) {
            con.setSchema(this.schema);
        }
    }

    @Override
    public Statement createStatement() throws SQLException {
        Statement st = new MycatStatement(this);
        return st;
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return new MycatStatement(this, resultSetType, resultSetConcurrency);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        return new MycatStatement(this, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return this.autoCommit;
    }

    @Override
    public int getHoldability() throws SQLException {
        return this.holdability;
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return this.transactionIsolation;
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        if (realConn != null) {
            return realConn.getTypeMap();
        } else {
            return this.typeMap;
        }
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return this.readOnly;
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        this.catalog = catalog;
    }

    @Override
    public String getCatalog() throws SQLException {
        if (realConn != null) {
            return realConn.getCatalog();
        } else {
            return "";
        }
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        this.holdability = holdability;
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        this.readOnly = readOnly;
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        this.transactionIsolation = level;
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        this.typeMap = map;
    }

    @Override
    public String getSchema() throws SQLException {
        return this.realConn != null ? realConn.getSchema() : schema;
    }

    @Override
    public void setSchema(String schema) {
        this.schema = schema;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return this.closed;
    }

    public void setRealConn(Connection realConn, String type) throws SQLException {
        if (type.equals(Constant.RW.WRITE.name())) {
            local.set(realConn);
        }

        this.realConn = realConn;
        initRealConnection(realConn);
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        if (this.realConn != null) {
            return realConn.getMetaData();
        }
        throw SQLError.createSQLException("getMetaData can't execute");
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        if (this.realConn != null) {
            return realConn.getWarnings();
        }
        throw SQLError.createSQLException("getWarnings can't execute");
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return this.realConn != null ? realConn.nativeSQL(sql) : "";
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        if (this.realConn != null) {
            return realConn.prepareCall(sql);
        }
        throw SQLError.createSQLException("prepareCall can't execute");
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        if (this.realConn != null) {
            return realConn.prepareCall(sql, resultSetType, resultSetConcurrency);
        }
        throw SQLError.createSQLException("prepareCall can't execute");
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
            int resultSetHoldability) throws SQLException {
        if (this.realConn != null) {
            return realConn.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        throw SQLError.createSQLException("prepareCall can't execute");
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        if (this.realConn != null) {
            realConn.releaseSavepoint(savepoint);
        }
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        if (this.realConn != null) {
            realConn.rollback(savepoint);
        }
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        if (this.realConn != null) {
            return realConn.setSavepoint();
        }
        throw SQLError.createSQLException("setSavepoint can't execute");
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        if (this.realConn != null) {
            return realConn.setSavepoint(name);
        }
        throw SQLError.createSQLException("setSavepoint can't execute");
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        if (this.realConn != null) {
            return realConn.createArrayOf(typeName, elements);
        }
        throw SQLError.createSQLException("setSavepoint can't execute");
    }

    @Override
    public Blob createBlob() throws SQLException {
        if (this.realConn != null) {
            return realConn.createBlob();
        }
        throw SQLError.createSQLException("createBlob can't execute");
    }

    @Override
    public Clob createClob() throws SQLException {
        if (this.realConn != null) {
            return realConn.createClob();
        }
        throw SQLError.createSQLException("createClob can't execute");
    }

    @Override
    public NClob createNClob() throws SQLException {
        if (this.realConn != null) {
            return realConn.createNClob();
        }
        throw SQLError.createSQLException("createNClob can't execute");
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        if (this.realConn != null) {
            return realConn.createSQLXML();
        }
        throw SQLError.createSQLException("createSQLXML can't execute");
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        if (this.realConn != null) {
            return realConn.createStruct(typeName, attributes);
        }
        throw SQLError.createSQLException("createStruct can't execute");
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        if (this.realConn != null) {
            return realConn.getClientInfo();
        }
        throw SQLError.createSQLException("getClientInfo can't execute");
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return this.realConn != null ? realConn.getClientInfo(name) : "";
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return this.realConn != null ? realConn.isValid(timeout) : false;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (this.realConn != null) {
            return realConn.isWrapperFor(iface);
        }
        throw SQLError.createSQLException("isWrapperFor can't execute");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (this.realConn != null) {
            return realConn.unwrap(iface);
        }
        throw SQLError.createSQLException("unwrap can't execute");
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        if (this.realConn != null) {
            realConn.abort(executor);
        }
    }

    @Override
    public void clearWarnings() throws SQLException {
        if (this.realConn != null) {
            realConn.clearWarnings();
        }
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return this.realConn != null ? realConn.getNetworkTimeout() : 0;
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        if (this.realConn != null) {
            realConn.setNetworkTimeout(executor, milliseconds);
        }
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        if (this.realConn != null) {
            realConn.setClientInfo(properties);
        }
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        if (this.realConn != null) {
            realConn.setClientInfo(name, value);
        }
    }

    public Connection getRealConn() {
        return realConn;
    }

}
