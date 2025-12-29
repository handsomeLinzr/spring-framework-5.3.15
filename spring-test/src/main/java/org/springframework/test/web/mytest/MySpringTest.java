package org.springframework.test.web.mytest;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.web.mytest.con.MyBean;
import org.springframework.test.web.mytest.con.MyConfig;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/**
 * @author linzherong
 * @date 2025/9/9 21:41
 */
public class MySpringTest {

	public static void main(String[] args) {
		ApplicationContext context = new AnnotationConfigApplicationContext(MyConfig.class);
		MyBean bean = context.getBean(MyBean.class);
		System.out.println(bean);
	}

}
