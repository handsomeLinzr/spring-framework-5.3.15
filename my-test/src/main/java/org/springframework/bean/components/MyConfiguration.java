package org.springframework.bean.components;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author linzherong
 * @date 2026/1/4 11:25
 */
@Configuration
public class MyConfiguration {

	@Bean
	public MyBean2 myBean2() {
		return new MyBean2();
	}


}
