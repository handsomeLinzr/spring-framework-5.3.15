package org.springframework.mytest;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.mytest.bean.Bean1;
import org.springframework.mytest.bean.Bean5;
import org.springframework.mytest.bean.aop.MyCalculate;
import org.springframework.mytest.event.*;
import org.springframework.mytest.lookup.LookupBean1;
import org.springframework.mytest.tx.BookService;
import org.springframework.mytest.tx.TxConfig;

/**
 * @author linzherong
 * @date 2025/12/24 23:58
 */
public class MyTest {

	public static void main(String[] args) {
		lookup();
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

	public static void txAnno() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TxConfig.class);
		BookService bean = context.getBean(BookService.class);
		bean.update(1,"100000000 ", "一个亿");
	}

	public static void event() {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("classpath:application.xml");
		ac.addApplicationListener(new MyApplicationListener());
		ac.publishEvent(new MyApplicationEvent("这是一个新事件"));
	}


	public static void eventAnno() {
		AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext(EventConfiguration.class);
		ac.publishEvent(new MyApplicationEvent("发布事件"));
	}

	public static void lookup() {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("classpath:lookup-method.xml");
		LookupBean1 lookupBean1 = ac.getBean(LookupBean1.class);
		lookupBean1.va();
		LookupBean1 lookupBean2 = ac.getBean(LookupBean1.class);
		lookupBean2.va();
	}


}
