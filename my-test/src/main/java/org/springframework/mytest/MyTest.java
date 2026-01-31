package org.springframework.mytest;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author linzherong
 * @date 2025/12/24 23:58
 */
public class MyTest {

	public static void main(String[] args) {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("classpath:application.xml");
		Object bean1 = ac.getBean("bean1");
		Object bean2 = ac.getBean("bean2");
		Object bean3 = ac.getBean("bean3");
		Object bean32 = ac.getBean("bean3");
		Object bean4 = ac.getBean("factoryBean4Bean4");
		Object bean42 = ac.getBean("factoryBean4Bean4");
		Object factoryBean4Bean4 = ac.getBean("&factoryBean4Bean4");
		Object factoryBean4Bean2 = ac.getBean("&factoryBean4Bean4");
		System.out.println(bean1);
	}

}
