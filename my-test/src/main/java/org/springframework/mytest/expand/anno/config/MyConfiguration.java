package org.springframework.mytest.expand.anno.config;

import org.springframework.context.annotation.Conditional;
import org.springframework.mytest.expand.anno.bean.AbstractOs;
import org.springframework.mytest.expand.anno.bean.MacOs;
import org.springframework.mytest.expand.anno.bean.MyBean2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mytest.expand.anno.bean.WindowsOs;
import org.springframework.mytest.expand.anno.condition.MacCondition;
import org.springframework.mytest.expand.anno.condition.WindowsCondition;

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

	@Bean
	@Conditional({WindowsCondition.class})
	public AbstractOs windowsOs() {
		return new WindowsOs();
	}

	@Bean
	@Conditional({MacCondition.class})
	public AbstractOs macOs() {
		return new MacOs();
	}


}
