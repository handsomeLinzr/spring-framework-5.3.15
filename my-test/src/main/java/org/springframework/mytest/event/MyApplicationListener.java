package org.springframework.mytest.event;

import org.springframework.context.ApplicationListener;

public class MyApplicationListener implements ApplicationListener<MyApplicationEvent> {

	@Override
	public void onApplicationEvent(MyApplicationEvent event) {
		System.out.println("接收到事件" + event + ":" + event.getMsg());
	}
}