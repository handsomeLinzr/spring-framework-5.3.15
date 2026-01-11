package org.springframework.mytest.expand.anno.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;
import org.springframework.stereotype.Component;

/**
 * @author linzherong
 * @date 2026/1/11 13:46
 */
@ComponentScans(
		@ComponentScan(basePackages = "org.springframework.mytest")
)
@Component
public class MyConfigurationComponentScans {
}
