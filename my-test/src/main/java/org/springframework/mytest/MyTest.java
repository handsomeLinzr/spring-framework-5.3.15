package org.springframework.mytest;

import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.mytest.bean.components.Bean1;
import org.springframework.mytest.bean.components.aop.MyCalculate;

/**
 * @author linzherong
 * @date 2025/12/24 23:58
 */
public class MyTest {

	public static void main(String[] args) {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("classpath:application.xml");
		MyCalculate mc = (MyCalculate) ac.getBean("myCalculate");
		int add = mc.add(1, 2);
		System.out.println(add);
//		Bean2 bean2 = (Bean2) ac.getBean("bean2");
//		Object bean3 = ac.getBean("bean3");
//		Object bean32 = ac.getBean("bean3");
//		Object bean4 = ac.getBean("factoryBean4Bean4");
//		Object bean42 = ac.getBean("factoryBean4Bean4");
//		Object factoryBean4Bean4 = ac.getBean("&factoryBean4Bean4");
//		Object factoryBean4Bean2 = ac.getBean("&factoryBean4Bean4");
//		bean1.run();
//		bean2.say();
	}

}
