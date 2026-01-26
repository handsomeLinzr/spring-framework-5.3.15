package org.springframework.mytest.bean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/1/24 21:12
 */
@Component
public class Bean5 {

	private Bean6 bean6;
	private MyBean1 myBean1;

	public Bean5() {

	}

	public Bean5(Bean6 bean6) {
		this.bean6 = bean6;
	}

	public Bean5(Bean6 bean6, MyBean1 myBean1) {
		this.bean6 = bean6;
		this.myBean1 = myBean1;
	}

}

