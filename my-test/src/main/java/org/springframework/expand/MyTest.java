package org.springframework.expand;

import org.springframework.bean.MyBean1;
import org.springframework.bean.MyBean4;
import org.springframework.context.ApplicationContext;

/**
 * @author linzherong
 * @date 2025/12/24 23:58
 */
public class MyTest {

	public static void main(String[] args) {
		ApplicationContext context = new MyClassPathXmlApplicationContext("classpath:application.xml");
		Object myBean2 = context.getBean("myBean2");
		MyUser myUser = context.getBean(MyUser.class);
		System.out.println(myUser);
		MyBean4 myBean4 = context.getBean(MyBean4.class);
		System.out.println(myBean4);
		MyBean1 bean1 = context.getBean(MyBean1.class);
		System.out.println(bean1);
	}

}
