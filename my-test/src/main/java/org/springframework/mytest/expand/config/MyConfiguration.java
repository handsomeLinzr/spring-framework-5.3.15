package org.springframework.mytest.expand.config;

import org.springframework.context.annotation.Conditional;
import org.springframework.mytest.bean.components.Bean1;
import org.springframework.mytest.bean.components.Bean2;
import org.springframework.mytest.bean.components.Bean3;
import org.springframework.mytest.expand.anno.bean.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mytest.expand.anno.condition.MacCondition;
import org.springframework.mytest.expand.anno.condition.WindowsCondition;

/**
 * @author linzherong
 * @date 2026/1/4 11:25
// */
//@Configuration
public class MyConfiguration {

	@Bean
	public Bean1 bean1() {
		return new Bean1();
	}

	@Bean
	public Bean1 bean1(Bean2 bean2, Bean3 bean3) {
		return new Bean1(bean2, bean3);
	}

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
