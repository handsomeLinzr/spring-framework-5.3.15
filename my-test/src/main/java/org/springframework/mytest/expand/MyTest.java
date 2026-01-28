package org.springframework.mytest.expand;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author linzherong
 * @date 2025/12/24 23:58
 */
public class MyTest {

	public static void main(String[] args) {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("classpath:application.xml");
		Object bean1 = ac.getBean("bean1");
		System.out.println(bean1);
	}

}
