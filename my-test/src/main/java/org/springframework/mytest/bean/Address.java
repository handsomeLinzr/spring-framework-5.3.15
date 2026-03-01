package org.springframework.mytest.bean;

/**
 * @author linzherong
 * @date 2026/3/1 20:17
 */
public class Address {

	private String province;

	private String city;

	private String district;

	public String getProvince() {
		return province;
	}

	public void setProvince(String province) {
		this.province = province;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getDistrict() {
		return district;
	}

	public void setDistrict(String district) {
		this.district = district;
	}
}
