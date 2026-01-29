package org.springframework.mytest.bean.components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/1/28 21:11
 */
@Component
public class Bean1 {

	private Bean2 bean2;
	private Bean3 bean3;

	public Bean1(Bean2 bean2, Bean3 bean3) {
		this.bean2 = bean2;
		this.bean3 = bean3;
	}

	@Autowired
	public Bean1(Bean2 bean2) {
		this.bean2 = bean2;
	}

	public Bean1() {

	}

}
