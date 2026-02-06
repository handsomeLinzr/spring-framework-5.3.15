package org.springframework.mytest.bean.components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/1/28 21:11
 */
@Component
public class Bean1 {

	@Autowired
	private Bean2 bean2;

//	public void run() {
//		System.out.println(bean2);
//		System.out.println("bean1");
//	}

//	@Resource
//	private Bean3 bean3;
//
//	public Bean2 getBean2() {
//		return bean2;
//	}
//
//	public void setBean2(Bean2 bean2) {
//		this.bean2 = bean2;
//	}
//
//	public Bean3 getBean3() {
//		return bean3;
//	}
//
//	public void setBean3(Bean3 bean3) {
//		this.bean3 = bean3;
//	}
}
