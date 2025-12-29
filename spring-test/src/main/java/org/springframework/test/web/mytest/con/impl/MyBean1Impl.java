package org.springframework.test.web.mytest.con.impl;

import org.springframework.stereotype.Service;
import org.springframework.test.web.mytest.con.MyBean1;

/**
 * @author linzherong
 * @date 2025/9/9 21:56
 */
@Service
public class MyBean1Impl implements MyBean1 {
	@Override
	public void say() {
		System.out.println("MyBean1");
	}
}
