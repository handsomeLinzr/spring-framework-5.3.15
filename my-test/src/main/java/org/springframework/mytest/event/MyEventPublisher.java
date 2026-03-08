package org.springframework.mytest.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/3/7 15:16
 */
@Component
public class MyEventPublisher {

	public MyEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	private final ApplicationEventPublisher publisher;


	public void publish(String msg) {
		publisher.publishEvent(new MyApplicationEvent(msg));
	}

}
