package org.springframework.mytest.expand;

import org.springframework.mytest.expand.anno.bean.*;
import org.springframework.mytest.expand.xmltag.bean.MyUser;

/**
 * @author linzherong
 * @date 2025/12/24 23:58
 */
public class MyTest {

	public static void main(String[] args) {
		MyClassPathXmlApplicationContext context = new MyClassPathXmlApplicationContext("classpath:application.xml");
//		Object myBean1 = context.getBean("myBean1");
//		System.out.println(myBean1);
//		MyUser myUser = context.getBean(MyUser.class);
//		System.out.println(myUser);
//		AbstractOs os = context.getBean(AbstractOs.class);
//		System.out.println(os);
		MyBean2 bean2 = context.getBean(MyBean2.class);
		MyBean2 bean22 = context.getBean(MyBean2.class);
		System.out.println(bean2 == bean22);
		System.out.println(bean2.myBean5 == bean22.myBean5);
		MyBean5 bean5 = context.getBean(MyBean5.class);

		System.out.println(bean2.myBean5 == bean5);

		MyBean6 bean61 = context.getBean(MyBean6.class);
		MyBean6 bean62 = context.getBean(MyBean6.class);
		Object bean63 = context.getBean("myFactoryBean");
		Object bean64 = context.getBean("&myFactoryBean");
		MyFactoryBean bean65 = context.getBean(MyFactoryBean.class);
		System.out.println(bean62);


	}

}
