package org.springframework.bean;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * @author linzherong
 * @date 2026/1/4 11:25
 */
@Configuration
public class MyConfiguration {

	// 这里在创建 bean 的时候，构造函数会自动获取到当前 bean 工厂中的 Spring 类型的 bean（下边的“默认”)，从而完成注入
	// 所以这个 bean 生成后，默认的 defaultName 就是“默认” 这个 bean 对象
	@Bean
	public MyBean4 myBean4() {
		return new MyBean4("默认名称");
	}

//	// 这里定义了一个默认的 String 类型的 bean，叫做 "默认"
//	@Bean
//	public String defaultNa() {
//		return new String("默认");
//	}

}
