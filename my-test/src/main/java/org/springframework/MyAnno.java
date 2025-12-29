package org.springframework;

import org.springframework.bean.MyBean2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author linzherong
 * @date 2025/11/18 13:12
 */
@Configuration
@ComponentScan
@EnableAsync
public class MyAnno {

	@Bean
	public MyBean2 myBean2() {
		return new MyBean2();
	}

}
