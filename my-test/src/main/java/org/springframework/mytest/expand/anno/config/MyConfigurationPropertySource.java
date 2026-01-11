package org.springframework.mytest.expand.anno.config;

import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/1/11 01:43
 */
@Component
@PropertySource("jdbc3.properties")
public class MyConfigurationPropertySource {
}
