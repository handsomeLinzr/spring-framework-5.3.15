package org.springframework.expand;

import org.springframework.bean.MyBean1;
import org.springframework.bean.components.MyBean2;
import org.springframework.bean.components.MyBean4;
import org.springframework.context.ApplicationContext;

/**
 * @author linzherong
 * @date 2025/12/24 23:58
 */
public class MyTest {

	public static void main(String[] args) {
		ApplicationContext context = new MyClassPathXmlApplicationContext("classpath:application.xml");
//		Object myBean1 = context.getBean("myBean1");
//		System.out.println(myBean1);
//		MyUser myUser = context.getBean(MyUser.class);
//		System.out.println(myUser);
		MyBean2 bean2 = context.getBean(MyBean2.class);
		System.out.println(bean2);
	}

}
