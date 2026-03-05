package org.springframework.mytest.tx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;

/**
 * @author linzherong
 * @date 2026/2/10 17:22
 */
@Component
public class BookDao {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public void updateNameById(int id, String name) {
		String sql = "UPDATE book SET book_name = ? WHERE id = ?";
		jdbcTemplate.update(sql, name, id);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateOrderNoById(int id, String orderNo) {
		String sql = "UPDATE book SET order_no = ? WHERE id = ?";
//		int i = 1/0;
		jdbcTemplate.update(sql, orderNo, id);

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				System.out.println("new 事务提交");
			}
		});

	}

	public String getOrderNo(int id) {
		String sql = "SELECT order_no FROM book WHERE id = ?";
		return jdbcTemplate.queryForObject(sql, String.class, id);
	}

	public String getName(int id) {
		String sql = "SELECT book_name FROM book WHERE id = ?";
		return jdbcTemplate.queryForObject(sql, String.class, id);
	}

	public int insert(String orderNo, String name) {
		String sql = "INSERT INTO book (order_no, book_name) VALUES (?, ?)";
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(con -> {
			PreparedStatement ps = con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
			ps.setString(1, orderNo);
			ps.setString(2, name);
			return ps;
		}, keyHolder);
		return keyHolder.getKey().intValue();
	}

}
