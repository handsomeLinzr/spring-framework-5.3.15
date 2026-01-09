package org.springframework.expand;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.expand.xmltag.bean.MyUser;

/**
 * 用作做扩展的 demo
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


	@Override
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {

		addBeanFactoryPostProcessor(beanFactory1 -> System.out.println("==============扩展 BEPP ======================"));

		beanFactory.addBeanPostProcessor(new BeanPostProcessor() {
			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
				if (bean instanceof MyUser) {
					System.out.println("=============扩展 beanPostProcessor 获取到 myUser ======================：" + bean);
				}
				return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
			}
		});
	}
}
