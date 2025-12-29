package org.springframework;

import org.springframework.bean.MyBean2;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author linzherong
 * @date 2025/11/18 12:47
 */
public class Main {
	public static void main(String[] args) {
		ApplicationContext context = new ClassPathXmlApplicationContext("classpath:application.xml");
		MyBean2 myBean2 = context.getBean(MyBean2.class);
		System.out.println(myBean2);
	}
}