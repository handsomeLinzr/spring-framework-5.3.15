/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.jdbc.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

import org.springframework.lang.Nullable;
import org.springframework.transaction.support.ResourceHolderSupport;
import org.springframework.util.Assert;

/**
 * Resource holder wrapping a JDBC {@link Connection}.
 * {@link DataSourceTransactionManager} binds instances of this class
 * to the thread, for a specific {@link javax.sql.DataSource}.
 *
 * <p>Inherits rollback-only support for nested JDBC transactions
 * and reference count functionality from the base class.
 *
 * <p>Note: This is an SPI class, not intended to be used by applications.
 *
 * @author Juergen Hoeller
 * @since 06.05.2003
 * @see DataSourceTransactionManager
 * @see DataSourceUtils
 */
public class ConnectionHolder extends ResourceHolderSupport {

	// 保存点设置的名称前缀
	/**
	 * Prefix for savepoint names.
	 */
	public static final String SAVEPOINT_NAME_PREFIX = "SAVEPOINT_";


	// 包装成一个 SimpleConnectionHandle，内部包装了具体的数据连接 connection
	@Nullable
	private ConnectionHandle connectionHandle;

	// 存放的是对应持有的 connection，也就是上边 connectionHandle 中包装的 connection
	@Nullable
	private Connection currentConnection;

	// 连接的事务激活状态，开始后会激活
	private boolean transactionActive = false;

	@Nullable
	private Boolean savepointsSupported;

	// 当前连接被设置的保存点的数量
	private int savepointCounter = 0;


	/**
	 * Create a new ConnectionHolder for the given ConnectionHandle.
	 * @param connectionHandle the ConnectionHandle to hold
	 */
	public ConnectionHolder(ConnectionHandle connectionHandle) {
		Assert.notNull(connectionHandle, "ConnectionHandle must not be null");
		this.connectionHandle = connectionHandle;
	}

	// 通过给定的 jdbc连接，创建一个新的连接持有器
	// 包装成一个 SimpleConnectionHandle
	/**
	 * Create a new ConnectionHolder for the given JDBC Connection,
	 * wrapping it with a {@link SimpleConnectionHandle},
	 * assuming that there is no ongoing transaction.
	 * @param connection the JDBC Connection to hold
	 * @see SimpleConnectionHandle
	 * @see #ConnectionHolder(java.sql.Connection, boolean)
	 */
	public ConnectionHolder(Connection connection) {
		this.connectionHandle = new SimpleConnectionHandle(connection);
	}

	/**
	 * Create a new ConnectionHolder for the given JDBC Connection,
	 * wrapping it with a {@link SimpleConnectionHandle}.
	 * @param connection the JDBC Connection to hold
	 * @param transactionActive whether the given Connection is involved
	 * in an ongoing transaction
	 * @see SimpleConnectionHandle
	 */
	public ConnectionHolder(Connection connection, boolean transactionActive) {
		this(connection);
		this.transactionActive = transactionActive;
	}


	/**
	 * Return the ConnectionHandle held by this ConnectionHolder.
	 */
	@Nullable
	public ConnectionHandle getConnectionHandle() {
		return this.connectionHandle;
	}

	/**
	 * Return whether this holder currently has a Connection.
	 */
	protected boolean hasConnection() {
		return (this.connectionHandle != null);
	}

	/**
	 * Set whether this holder represents an active, JDBC-managed transaction.
	 * @see DataSourceTransactionManager
	 */
	protected void setTransactionActive(boolean transactionActive) {
		this.transactionActive = transactionActive;
	}

	/**
	 * Return whether this holder represents an active, JDBC-managed transaction.
	 */
	protected boolean isTransactionActive() {
		return this.transactionActive;
	}


	/**
	 * Override the existing Connection handle with the given Connection.
	 * Reset the handle if given {@code null}.
	 * <p>Used for releasing the Connection on suspend (with a {@code null}
	 * argument) and setting a fresh Connection on resume.
	 */
	protected void setConnection(@Nullable Connection connection) {
		if (this.currentConnection != null) {
			if (this.connectionHandle != null) {
				this.connectionHandle.releaseConnection(this.currentConnection);
			}
			this.currentConnection = null;
		}
		if (connection != null) {
			this.connectionHandle = new SimpleConnectionHandle(connection);
		}
		else {
			this.connectionHandle = null;
		}
	}

	/**
	 * Return the current Connection held by this ConnectionHolder.
	 * <p>This will be the same Connection until {@code released}
	 * gets called on the ConnectionHolder, which will reset the
	 * held Connection, fetching a new Connection on demand.
	 * @see ConnectionHandle#getConnection()
	 * @see #released()
	 */
	public Connection getConnection() {
		Assert.notNull(this.connectionHandle, "Active Connection is required");
		if (this.currentConnection == null) {
			// 设置为当前持有的连接器
			this.currentConnection = this.connectionHandle.getConnection();
		}
		// 返回这个连接器
		return this.currentConnection;
	}

	/**
	 * Return whether JDBC 3.0 Savepoints are supported.
	 * Caches the flag for the lifetime of this ConnectionHolder.
	 * @throws SQLException if thrown by the JDBC driver
	 */
	public boolean supportsSavepoints() throws SQLException {
		if (this.savepointsSupported == null) {
			this.savepointsSupported = getConnection().getMetaData().supportsSavepoints();
		}
		return this.savepointsSupported;
	}

	/**
	 * Create a new JDBC 3.0 Savepoint for the current Connection,
	 * using generated savepoint names that are unique for the Connection.
	 * @return the new Savepoint
	 * @throws SQLException if thrown by the JDBC driver
	 */
	public Savepoint createSavepoint() throws SQLException {
		// 保存点设置数量 +1
		this.savepointCounter++;
		// 调用 API，设置保存点，名称是 SAVEPOINT_ + 数量
		// 返回对应的保存点信息
		return getConnection().setSavepoint(SAVEPOINT_NAME_PREFIX + this.savepointCounter);
	}

	/**
	 * Releases the current Connection held by this ConnectionHolder.
	 * <p>This is necessary for ConnectionHandles that expect "Connection borrowing",
	 * where each returned Connection is only temporarily leased and needs to be
	 * returned once the data operation is done, to make the Connection available
	 * for other operations within the same transaction.
	 */
	@Override
	public void released() {
		super.released();
		// isOpen() 判断当前这个连接被引用的数量是不是 >0，如果是，则说明还有其他对象在引用这个连接
		// 这里判断如果当前这个连接没有被其他对象引用了，且 currentConnection 不为空
		if (!isOpen() && this.currentConnection != null) {
			if (this.connectionHandle != null) {
				// 调用 SimpleConnectionHandle 进行释放，默认什么都不做
				this.connectionHandle.releaseConnection(this.currentConnection);
			}
			// 清空这个 currentConnection
			this.currentConnection = null;
		}
	}


	@Override
	public void clear() {
		super.clear();
		this.transactionActive = false;
		this.savepointsSupported = null;
		this.savepointCounter = 0;
	}

}
