package org.springframework.mytest.tx;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mytest.event.EventConfiguration;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * @author linzherong
 * @date 2026/3/5 21:22
 */
@Configuration
@ComponentScan
@EnableTransactionManagement
@Import(EventConfiguration.class)
public class TxConfig {

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	@Bean
	public DataSource dataSource() {
		DruidDataSource dataSource = new DruidDataSource();

		// 数据库连接配置
		dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
		dataSource.setUrl("jdbc:mysql://localhost:3306/my-test?useSSL=false&serverTimezone=UTC&characterEncoding=utf8");
		dataSource.setUsername("root");
		dataSource.setPassword("87734591Aa.");

		// 连接池配置
		dataSource.setInitialSize(5);
		dataSource.setMinIdle(5);
		dataSource.setMaxActive(20);
		dataSource.setMaxWait(60000);
		dataSource.setTimeBetweenEvictionRunsMillis(60000);
		dataSource.setMinEvictableIdleTimeMillis(300000);

		// 连接验证配置
		dataSource.setValidationQuery("SELECT 1");
		dataSource.setTestWhileIdle(true);
		dataSource.setTestOnBorrow(false);
		dataSource.setTestOnReturn(false);

		return dataSource;
	}

	@Bean
	public TransactionManager transactionManager(DataSource dataSource) {
		return new DataSourceTransactionManager(dataSource);
	}

}
