package org.springframework.mytest.expand.anno.bean;

/**
 * @author linzherong
 * @date 2025/11/18 13:33
 */
public class MyBean2 {

	public MyBean5 myBean5;

	public MyBean2(MyBean5 bean5) {
		this.myBean5 = bean5;
	}

}
