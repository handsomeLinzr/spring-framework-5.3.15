package org.springframework.mytest.factory;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.mytest.bean.Bean4;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/1/30 15:15
 */
@Component
public class FactoryBean4Bean4 implements FactoryBean<Bean4> {


	@Override
	public Bean4 getObject() throws Exception {
		return new Bean4();
	}

	@Override
	public Class<?> getObjectType() {
		return Bean4.class;
	}

	@Override
	public boolean isSingleton() {
		return false;
	}
}
