package org.springframework.mytest.expand.editor;

import org.springframework.mytest.bean.Address;

import java.beans.PropertyEditorSupport;

/**
 * 地址属性编辑器
 * @author linzherong
 * @date 2026/3/1 20:38
 */
public class AddressPropertyEditor extends PropertyEditorSupport {

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		Address address = new Address();
		String[] split = text.split("_");
		address.setProvince(split[0]);
		address.setCity(split[1]);
		address.setDistrict(split[2]);
		setValue(address);
	}
}
