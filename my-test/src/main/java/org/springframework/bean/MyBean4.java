package org.springframework.bean;

/**
 * @author linzherong
 * @date 2026/1/4 11:25
 */
public class MyBean4 {

	private String defaultName;

	public MyBean4(String defaultName) {
		this.defaultName = defaultName;
	}

	public String getDefaultName() {
		return defaultName;
	}

	public void setDefaultName(String defaultName) {
		this.defaultName = defaultName;
	}

	@Override
	public String toString() {
		return "MyBean4{" +
				"defaultName='" + defaultName + '\'' +
				'}';
	}
}
