package org.springframework.mytest.event;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.*;
import org.springframework.mytest.expand.anno.condition.WindowsCondition;
import org.springframework.transaction.config.TransactionManagementConfigUtils;
import org.springframework.transaction.event.TransactionalApplicationListener;
import org.springframework.transaction.event.TransactionalApplicationListenerMethodAdapter;
import org.springframework.transaction.event.TransactionalEventListenerFactory;

import java.lang.reflect.Method;

/**
 * @author linzherong
 * @date 2026/3/7 15:18
 */
@Configuration
@ComponentScan
public class EventConfiguration {


	// 事务的监听器创建工厂
	@Bean(name = TransactionManagementConfigUtils.TRANSACTIONAL_EVENT_LISTENER_FACTORY_BEAN_NAME)
	public TransactionalEventListenerFactory transactionalEventListenerFactory() {
		return new MyTransactionalEventListenerFactory();
	}

	static class MyTransactionalEventListenerFactory extends TransactionalEventListenerFactory {

		@Override
		public ApplicationListener<?> createApplicationListener(String beanName, Class<?> type, Method method) {
			TransactionalApplicationListenerMethodAdapter listener = (TransactionalApplicationListenerMethodAdapter) super.createApplicationListener(beanName, type, method);
			listener.addCallback(new TransactionalApplicationListener.SynchronizationCallback() {
				@Override
				public void preProcessEvent(ApplicationEvent event) {
					System.out.println("ProcessEvent 前");
				}

				@Override
				public void postProcessEvent(ApplicationEvent event, Throwable ex) {
					System.out.println("ProcessEvent 后");
				}
			});
			return listener;
		}
	}

}
