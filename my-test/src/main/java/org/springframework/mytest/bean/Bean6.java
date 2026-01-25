package org.springframework.mytest.bean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/1/24 21:12
 */
@Component
public class Bean6 {

	@Autowired
	private Bean5 bean5;

}
