package org.springframework.mytest.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * @author linzherong
 * @date 2026/3/7 15:15
 */
@Component
public class MyApplicationListener2 {

//	@EventListener
//	public void onMyEvent(MyApplicationEvent event) {
//		System.out.println("收到事件"+event + ":" + event.getMsg());
//	}

	@TransactionalEventListener
	public void onTransactionEvent(MyApplicationEvent event) {
		System.out.println("收到事务事件"+event + ":" + event.getMsg());
	}

}
