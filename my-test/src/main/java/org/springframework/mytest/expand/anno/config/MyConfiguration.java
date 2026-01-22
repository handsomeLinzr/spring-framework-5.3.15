package org.springframework.mytest.expand.anno.config;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.mytest.bean.components.MyBean3;
import org.springframework.mytest.expand.anno.bean.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mytest.expand.anno.condition.MacCondition;
import org.springframework.mytest.expand.anno.condition.WindowsCondition;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/1/4 11:25
// */
@Configuration
public class MyConfiguration {

	@Bean
	public MyBean2 myBean2() {
		return new MyBean2(myBean5());
	}

	@Bean
	public MyBean5 myBean5() {
		return new MyBean5();
	}

//	@Bean
//	public MyFactoryBean myFactoryBean() {
//		return new MyFactoryBean();
//	}

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
