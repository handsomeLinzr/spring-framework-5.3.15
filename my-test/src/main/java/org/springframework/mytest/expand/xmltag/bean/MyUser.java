package org.springframework.mytest.expand.xmltag.bean;

/**
 * @author linzherong
 * @date 2025/12/28 22:28
 */
public class MyUser {

	private String username;
	private String password;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}


	@Override
	public String toString() {
		return "MyUser{" +
				"username='" + username + '\'' +
				", password='" + password + '\'' +
				'}';
	}
}
