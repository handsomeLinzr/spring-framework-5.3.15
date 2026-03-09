package org.springframework.mytest.lookup;

import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/3/9 18:34
 */
@Component
public abstract class LookupBean1 {

	@Lookup
	public abstract LookupBean2 getLookupBean2();

	public void va() {
		getLookupBean2().lookup2();
	}

}
