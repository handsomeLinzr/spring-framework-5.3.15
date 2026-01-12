package org.springframework.mytest.expand.anno.bean;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/1/12 18:29
 */
public class MyFactoryBean implements FactoryBean<MyBean6> {
	@Override
	public MyBean6 getObject() throws Exception {
		return new MyBean6();
	}

	@Override
	public Class<?> getObjectType() {
		return MyBean6.class;
	}
}
