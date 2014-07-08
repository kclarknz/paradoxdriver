package org.paradox;

import java.io.IOException;
import org.paradox.results.ColumnDTO;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import static org.paradox.data.TableData.listTables;
import static org.paradox.data.TableData.loadData;
import org.paradox.data.table.value.AbstractFieldValue;
import org.paradox.metadata.ParadoxField;
import org.paradox.metadata.ParadoxTable;
import org.paradox.parser.SQLParser;
import org.paradox.parser.nodes.FieldNode;
import org.paradox.parser.nodes.SQLNode;
import org.paradox.parser.nodes.SelectNode;
import org.paradox.parser.nodes.TableNode;
import org.paradox.utils.SQLStates;

/**
 * Statement para o PARADOX
 *
 * @author Leonardo Alves da Costa
 * @version 1.0
 * @since 15/03/2009
 */
public class ParadoxStatement implements Statement {

    private static final long serialVersionUID = -3859911934504328460L;
    private final ParadoxConnection conn;
    /**
     * Se este Statement está fechado
     */
    private boolean closed = false;
    private int maxFieldSize = 255;
    private int maxRows = 0;
    private SQLWarning warnings = null;
    private boolean poolable = false;
    private int fetchSize = 10;
    private boolean escapeProcessing = true;
    private ParadoxResultSet rs = null;
    private int fetchDirection = ResultSet.FETCH_FORWARD;
    private int queryTimeout = 20;
    String cursorName = "NO_NAME";

    public ParadoxStatement(final ParadoxConnection conn) {
        this.conn = conn;
    }

    @Override
    public ResultSet executeQuery(final String sql) throws SQLException {
        if (rs != null && !rs.isClosed()) {
            rs.close();
        }
        try {
            final SQLParser parser = new SQLParser(sql);
            final SQLNode rootNode = parser.parse();

            if (rootNode instanceof SelectNode) {
                final SelectNode node = (SelectNode) rootNode;
                final ArrayList<FieldNode> fields = node.getFields();
                //final ArrayList<SQLNode> where = node.getWhere();
                final ArrayList<TableNode> from = node.getTables();

                if (from.size() != 1) {
                    throw new SQLException("Somente uma tabela é suportado", SQLStates.INVALID_SQL);
                }

                // Generate SQL Tree
                tables:
                for (final TableNode tableNode : from) {
                    for (final ParadoxTable table : listTables(conn)) {
                        if (table.getName().equalsIgnoreCase(tableNode.getName())) {
                            tableNode.setTable(table);
                            continue tables;
                        }
                    }
                    throw new SQLException("Tabela " + tableNode.getName() + " não encontrada.", SQLStates.INVALID_SQL);
                }

                final ArrayList<ColumnDTO> columns = new ArrayList<ColumnDTO>();

                for (final FieldNode field : fields) {
                    if ("*".equals(field.getName())) {
                        for (final TableNode tableNode : from) {
                            for (final ParadoxField tableField : tableNode.getTable().getFields()) {
                                columns.add(tableField.getColumn());
                            }
                        }
                    }
                    for (final TableNode tableNode : from) {
                        for (final ParadoxField tableField : tableNode.getTable().getFields()) {
                            if (tableField.getName().equalsIgnoreCase(field.getName())) {
                                columns.add(tableField.getColumn());
                            }
                        }
                    }
                }

                // Generate SQL Plan
                // FIXME Generate SQL Plan

                // Execute plan
                // FIXME more than one table
                final ArrayList<ParadoxField> fieldList = new ArrayList<ParadoxField>();
                for (final ColumnDTO column : columns) {
                    for (final ParadoxField field : from.get(0).getTable().getFields()) {
                        if (field.getName().equalsIgnoreCase(column.getName())) {
                            fieldList.add(field);
                        }
                    }
                }
                final ArrayList<ArrayList<AbstractFieldValue>> values = loadData(conn, from.get(0).getTable(), fieldList);

                rs = new ParadoxResultSet(conn, this, values, columns);
                return rs;
            } else {
                throw new SQLException("Not a SELECT statement", SQLStates.INVALID_COMMAND);
            }
        } catch (final SQLException e) {
            throw new SQLException(e.getMessage() + ": " + sql, SQLStates.INVALID_SQL, e);
        } catch (final IOException e) {
            throw new SQLException(e.getMessage() + ": " + sql, SQLStates.INVALID_SQL, e);
        }
    }

    @Override
    public int executeUpdate(final String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void close() throws SQLException {
        if (rs != null && !rs.isClosed()) {
            rs.close();
        }
        closed = true;
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return maxFieldSize;
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        if (max > 255) {
            throw new SQLException("Value bigger than 255.", SQLStates.INVALID_PARAMETER);
        }
        maxFieldSize = max;
    }

    @Override
    public int getMaxRows() throws SQLException {
        return maxRows;
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        maxRows = max;
    }

    @Override
    public void setEscapeProcessing(final boolean enable) throws SQLException {
        escapeProcessing = enable;
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return queryTimeout;
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        queryTimeout = seconds;
    }

    @Override
    public void cancel() throws SQLException {
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return warnings;
    }

    @Override
    public void clearWarnings() throws SQLException {
        warnings = null;
    }

    @Override
    public void setCursorName(final String name) throws SQLException {
        cursorName = name;
    }

    @Override
    public boolean execute(final String sql) throws SQLException {
        // FIXME detectar o tipo de SQL
        rs = (ParadoxResultSet)executeQuery(sql);
        return true;
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return rs;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return -1;
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return false;
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        if (direction != ResultSet.FETCH_FORWARD) {
            throw new SQLException("O resultset somente pode ser ResultSet.FETCH_FORWARD", SQLStates.INVALID_PARAMETER);
        }
        this.fetchDirection = direction;
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return fetchDirection;
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        this.fetchSize = rows;
    }

    @Override
    public int getFetchSize() throws SQLException {
        return fetchSize;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public int getResultSetType() throws SQLException {
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void clearBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public int[] executeBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return conn;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return false;
    }

    public ResultSet getGeneratedKeys() throws SQLException {
        return new ParadoxResultSet(conn, this, new ArrayList<ArrayList<AbstractFieldValue>>(), new ArrayList<ColumnDTO>());
    }

    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return 0;
    }

    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return 0;
    }

    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return 0;
    }

    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return execute(sql);
    }

    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return execute(sql);
    }

    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return execute(sql);
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return conn.getHoldability();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public void setPoolable(final boolean poolable) throws SQLException {
        this.poolable = poolable;
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return poolable;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (isWrapperFor(iface)) {
            return (T) this;
        }
        throw new SQLException("Type not found.", SQLStates.TYPE_NOT_FOUND);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return getClass().isAssignableFrom(iface);
    }

    public void closeOnCompletion() throws SQLException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public boolean isCloseOnCompletion() throws SQLException {
        return true;
    }
}
