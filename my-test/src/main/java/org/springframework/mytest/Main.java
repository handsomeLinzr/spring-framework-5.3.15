package org.springframework.mytest;

import org.springframework.mytest.bean.Bean5;
import org.springframework.mytest.bean.Bean6;
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
		Bean5 bean = context.getBean(Bean5.class);
		Bean6 bean1 = context.getBean(Bean6.class);
		System.out.println(bean1);
	}
}