package org.springframework.expand;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author linzherong
 * @date 2025/12/24 23:52
 */
public class MyClassPathXmlApplicationContext extends ClassPathXmlApplicationContext {

	public MyClassPathXmlApplicationContext(String configLocation) {
		super(configLocation);
	}

	@Override
	protected void initPropertySources() {
		System.out.println("==================>>>>>>>>>>扩展initPropertySources<<<<<<<<================");
		System.out.println("==================>>>>>>>>>>扩展customizeBeanFactory，这里allowBeanDefinitionOverriding设置true<<<<<<<<================");
		super.setAllowBeanDefinitionOverriding(true);
		System.out.println("==================>>>>>>>>>>扩展customizeBeanFactory，这里allowCircularReferences设置true<<<<<<<<================");
		super.setAllowCircularReferences(true);
	}

}
