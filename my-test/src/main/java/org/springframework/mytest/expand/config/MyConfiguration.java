package org.springframework.mytest.expand.config;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.mytest.bean.Bean2;
import org.springframework.mytest.bean.Bean3;
import org.springframework.context.annotation.Bean;

/**
 * @author linzherong
 * @date 2026/1/4 11:25
// */
//@Configuration
public class MyConfiguration {

	@Bean
	@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public Bean3 bean3(Bean2 bean222) {
		return new Bean3(bean222);
	}

//	@Bean
//	public Bean2 bean2() {
//		return new Bean2();
//	}


//	@Bean
//	public Bean1 bean1(Bean2 bean2, Bean3 bean3) {
//		return new Bean1(bean2, bean3);
//	}

//	@Bean
//	public Bean1 bean1(Bean2 bean2) {
//		return new Bean1(bean2);
//	}





//	@Bean
//	@Conditional({WindowsCondition.class})
//	public AbstractOs windowsOs() {
//		return new WindowsOs();
//	}
//
//	@Bean
//	@Conditional({MacCondition.class})
//	public AbstractOs macOs() {
//		return new MacOs();
//	}


}
