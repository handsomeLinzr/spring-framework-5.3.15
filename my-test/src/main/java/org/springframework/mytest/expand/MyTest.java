package org.springframework.mytest.expand;

import org.springframework.mytest.bean.components.MyBean3;
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
		MyBean2 bean2_1 = context.getBean(MyBean2.class);
		MyBean2 bean2_2 = context.getBean(MyBean2.class);
		System.out.println(bean2_1 == bean2_2);
		System.out.println(bean2_1.myBean5 == bean2_2.myBean5);
		MyBean5 bean5 = context.getBean(MyBean5.class);
		System.out.println(bean2_1.myBean5 == bean5);

		MyBean6 bean6_1 = context.getBean(MyBean6.class);
		MyBean6 bean6_2 = context.getBean(MyBean6.class);
		Object bean6_3 = context.getBean("myFactoryBean");
		Object factoryBean_1 = context.getBean("&myFactoryBean");
		Object factoryBean_2 = context.getBean("&myFactoryBean");
		System.out.println(bean6_2);

		MyBean3 myBean3_1= context.getBean(MyBean3.class);
		MyBean3 myBean3_2= context.getBean(MyBean3.class);


	}

}
