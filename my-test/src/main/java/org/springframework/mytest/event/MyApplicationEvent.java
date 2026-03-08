package org.springframework.mytest.event;

import org.springframework.context.ApplicationEvent;

public class MyApplicationEvent extends ApplicationEvent {

	private final String msg;

	public String getMsg() {
		return msg;
	}

	public MyApplicationEvent(Object source) {
		super(source);
		this.msg = (String) source;
	}

}