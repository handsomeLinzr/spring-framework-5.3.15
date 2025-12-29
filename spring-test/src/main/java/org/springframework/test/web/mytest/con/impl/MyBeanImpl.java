package org.springframework.test.web.mytest.con.impl;

import org.springframework.stereotype.Service;
import org.springframework.test.web.mytest.con.MyBean;

/**
 * @author linzherong
 * @date 2025/9/9 21:56
 */
@Service
public class MyBeanImpl implements MyBean {
	@Override
	public void say() {
		System.out.println("myBean");
	}
}
