package org.springframework.mytest.bean;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

/**
 * @author linzherong
 * @date 2026/1/24 21:12
 */
@Component
public class Bean6 {

	@Resource
	private Bean5 bean5;

	@PostConstruct
	public void init() {
		System.out.println("=====bean6 init ===");
	}

	@PreDestroy
	public void destroy() {
		System.out.println("========pre destroy========");
	}

}
