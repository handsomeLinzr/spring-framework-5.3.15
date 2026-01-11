package org.springframework.mytest.expand;

import org.springframework.mytest.expand.anno.bean.AbstractOs;
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
		MyUser myUser = context.getBean(MyUser.class);
		System.out.println(myUser);
		AbstractOs os = context.getBean(AbstractOs.class);
		System.out.println(os);
	}

}
