package org.springframework.mytest;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.mytest.bean.Bean1;
import org.springframework.mytest.bean.Bean5;
import org.springframework.mytest.bean.aop.MyCalculate;
import org.springframework.mytest.tx.BookService;

/**
 * @author linzherong
 * @date 2025/12/24 23:58
 */
public class MyTest {

	public static void main(String[] args) {
		customPropertyEdit();
	}


	public static void application() {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("classpath:application.xml");
		Object bean1 = ac.getBean("bean1");
		System.out.println(bean1);
	}

	public static void anno() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register();
		context.refresh();
	}

	public static void aop() {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("classpath:aop.xml");
		MyCalculate ca = ac.getBean(MyCalculate.class);
		int result = ca.add(1, 2);
		Bean1 bean = ac.getBean(Bean1.class);
		bean.test();
	}

	public static void tx() {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("classpath:tx.xml");
		BookService bookService = ac.getBean(BookService.class);
		bookService.update(1, "20260210001-02", "更改2");
		System.out.println(1);
//		bookService.update(1, "20260210001", "故事书籍2");
	}

	public static void customPropertyEdit() {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("classpath:custom-property-editor.xml");
		Bean5 bean5 = ac.getBean(Bean5.class);
		System.out.println(bean5.getAddress());
//		bookService.update(1, "20260210001", "故事书籍2");
	}


}
