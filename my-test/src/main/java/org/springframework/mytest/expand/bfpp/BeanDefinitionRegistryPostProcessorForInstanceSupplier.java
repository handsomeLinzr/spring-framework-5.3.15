package org.springframework.mytest.expand.bfpp;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.mytest.bean.components.Bean7;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 *
 * InstanceSupplier 方式创建 bean
 *
 * @author linzherong
 * @date 2026/1/26 21:53
 */
@Component
public class BeanDefinitionRegistryPostProcessorForInstanceSupplier implements BeanDefinitionRegistryPostProcessor {

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		GenericBeanDefinition bd = new GenericBeanDefinition();
		bd.setBeanClass(Bean7.class);
		bd.setInstanceSupplier((Supplier<Bean7>) () -> new Bean7("阿哲"));
		registry.registerBeanDefinition("myBean7", bd);
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

	}
}
