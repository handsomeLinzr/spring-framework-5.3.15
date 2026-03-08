/*
 * Copyright 2002-2020 the original author or authors.
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

import java.util.List;

import org.springframework.context.ApplicationEvent;
import org.springframework.transaction.support.TransactionSynchronization;

/**
 * {@link TransactionSynchronization} implementation for event processing with a
 * {@link TransactionalApplicationListener}.
 *
 * @author Juergen Hoeller
 * @since 5.3
 * @param <E> the specific {@code ApplicationEvent} subclass to listen to
 */
class TransactionalApplicationListenerSynchronization<E extends ApplicationEvent>
		implements TransactionSynchronization {

	private final E event;

	private final TransactionalApplicationListener<E> listener;

	private final List<TransactionalApplicationListener.SynchronizationCallback> callbacks;


	// 事务监听事件，注册到同步器中的对象
	public TransactionalApplicationListenerSynchronization(E event, TransactionalApplicationListener<E> listener,
			List<TransactionalApplicationListener.SynchronizationCallback> callbacks) {

		// 事件
		this.event = event;
		// 监听器对象
		this.listener = listener;
		// 回调
		this.callbacks = callbacks;
	}


	@Override
	public int getOrder() {
		return this.listener.getOrder();
	}

	// 提交前≤
	@Override
	public void beforeCommit(boolean readOnly) {
		// 判断当前监听器的监听阶段是 TransactionPhase.BEFORE_COMMIT，则执行回调
		if (this.listener.getTransactionPhase() == TransactionPhase.BEFORE_COMMIT) {
			processEventWithCallbacks();
		}
	}

	// 完成后
	@Override
	public void afterCompletion(int status) {
		// 获取当前监听器的监听阶段
		TransactionPhase phase = this.listener.getTransactionPhase();
		// 如果监听阶段属于 TransactionPhase.AFTER_COMMIT，且当前状态是 STATUS_COMMITTED
		if (phase == TransactionPhase.AFTER_COMMIT && status == STATUS_COMMITTED) {
			// 执行回调
			processEventWithCallbacks();
		}
		// 如果是 TransactionPhase.AFTER_ROLLBACK
		else if (phase == TransactionPhase.AFTER_ROLLBACK && status == STATUS_ROLLED_BACK) {
			processEventWithCallbacks();
		}
		// 如果是 TransactionPhase.AFTER_COMPLETION
		else if (phase == TransactionPhase.AFTER_COMPLETION) {
			processEventWithCallbacks();
		}
	}

	// 处理回调
	// 如果没有 callbacks，则直接调用 processEvent 方法
	private void processEventWithCallbacks() {
		// 遍历所有的 callback 回调，调用对应的 preProcessEvent 方法，将事件传入
		this.callbacks.forEach(callback -> callback.preProcessEvent(this.event));
		try {
			// 调用监听器的 processEvent 方法
			this.listener.processEvent(this.event);
		}
		catch (RuntimeException | Error ex) {
			this.callbacks.forEach(callback -> callback.postProcessEvent(this.event, ex));
			throw ex;
		}
		// 调用所有 callbacks 回调，调用对应的 postProcessEvent 方法
		this.callbacks.forEach(callback -> callback.postProcessEvent(this.event, null));
	}

}
