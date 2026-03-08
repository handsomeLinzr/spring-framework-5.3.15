/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.transaction.event;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ApplicationListenerMethodAdapter;
import org.springframework.context.event.EventListener;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

// TransactionalApplicationListenerMethodAdapter，也是继承了 ApplicationListenerMethodAdapter
/**
 * {@link GenericApplicationListener} adapter that delegates the processing of
 * an event to a {@link TransactionalEventListener} annotated method. Supports
 * the exact same features as any regular {@link EventListener} annotated method
 * but is aware of the transactional context of the event publisher.
 *
 * <p>Processing of {@link TransactionalEventListener} is enabled automatically
 * when Spring's transaction management is enabled. For other cases, registering
 * a bean of type {@link TransactionalEventListenerFactory} is required.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 5.3
 * @see TransactionalEventListener
 * @see TransactionalApplicationListener
 * @see TransactionalApplicationListenerAdapter
 */
public class TransactionalApplicationListenerMethodAdapter extends ApplicationListenerMethodAdapter
		implements TransactionalApplicationListener<ApplicationEvent> {

	// 对应方法上的注解
	private final TransactionalEventListener annotation;

	// 监听的阶段
	private final TransactionPhase transactionPhase;

	private final List<SynchronizationCallback> callbacks = new CopyOnWriteArrayList<>();


	/**
	 * Construct a new TransactionalApplicationListenerMethodAdapter.
	 * @param beanName the name of the bean to invoke the listener method on
	 * @param targetClass the target class that the method is declared on
	 * @param method the listener method to invoke
	 */
	public TransactionalApplicationListenerMethodAdapter(String beanName, Class<?> targetClass, Method method) {
		// 调用父类，ApplicationListenerMethodAdapter 构造函数，先进行统一的处理
		// 解析设置对应的方法、监听事件类型等属性
		super(beanName, targetClass, method);
		// 从 method 上获取注解 TransactionalEventListener 的信息
		TransactionalEventListener ann =
				AnnotatedElementUtils.findMergedAnnotation(method, TransactionalEventListener.class);
		if (ann == null) {
			// 如果没有这个注解，抛出异常
			throw new IllegalStateException("No TransactionalEventListener annotation found on method: " + method);
		}
		// 设置对应的注解
		this.annotation = ann;
		// 设置对应监听的阶段，默认是 TransactionPhase.AFTER_COMMIT 提交后处理
		this.transactionPhase = ann.phase();
	}


	@Override
	public TransactionPhase getTransactionPhase() {
		return this.transactionPhase;
	}

	// 添加回调方法
	@Override
	public void addCallback(SynchronizationCallback callback) {
		Assert.notNull(callback, "SynchronizationCallback must not be null");
		this.callbacks.add(callback);
	}


	// 事务监听器的处理逻辑
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		// 判断当前是否是 synchronizations 不为空，且当前的事务是有效激活的
		// 其实就是判断当前线程是否有事务
		if (TransactionSynchronizationManager.isSynchronizationActive() &&
				TransactionSynchronizationManager.isActualTransactionActive()) {
			// 有则创建 TransactionalApplicationListenerSynchronization 对象
			// 注册到当前的 synchronizations 中
			TransactionSynchronizationManager.registerSynchronization(
					new TransactionalApplicationListenerSynchronization<>(event, this, this.callbacks));
		}
		// 否则判断注解的 fallbackExecution，判断如果没有事务是否需要执行
		// 默认是 false，如果是 true 则继续往下走
		else if (this.annotation.fallbackExecution()) {
			// 打印日志
			if (this.annotation.phase() == TransactionPhase.AFTER_ROLLBACK && logger.isWarnEnabled()) {
				logger.warn("Processing " + event + " as a fallback execution on AFTER_ROLLBACK phase");
			}
			// 调用 processEvent 处理事件
			// 这里就调用到了父类 ApplicationListenerMethodAdapter 中了，和普通的事件监听一样逻辑
			processEvent(event);
		}
		else {
			// 打印日志
			// No transactional event execution at all
			if (logger.isDebugEnabled()) {
				logger.debug("No transaction is active - skipping " + event);
			}
		}
	}

}
