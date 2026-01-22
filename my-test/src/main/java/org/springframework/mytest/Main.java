package org.springframework.mytest;

import org.springframework.mytest.expand.anno.bean.MyBean2;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.mytest.expand.anno.bean.MyFactoryBean;

/**
 * @author linzherong
 * @date 2025/11/18 12:47
 */
public class Main {
	public static void main(String[] args) {
		ApplicationContext context = new ClassPathXmlApplicationContext("classpath:application.xml");
		MyBean2 myBean2 = context.getBean(MyBean2.class);
		MyFactoryBean factoryBean = context.getBean(MyFactoryBean.class);
		Object bean6 = context.getBean("myFactoryBean");
		System.out.println(factoryBean);
		System.out.println(myBean2);
	}
}