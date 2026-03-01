package org.springframework.mytest.expand.editor;

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.mytest.bean.Address;

/**
 *
 * 地址属性编辑注册器
 *
 * @author linzherong
 * @date 2026/3/1 20:37
 */
public class AddressPropertyEditorRegistrar implements PropertyEditorRegistrar {

	@Override
	public void registerCustomEditors(PropertyEditorRegistry registry) {
		registry.registerCustomEditor(Address.class, new AddressPropertyEditor());
	}
}
