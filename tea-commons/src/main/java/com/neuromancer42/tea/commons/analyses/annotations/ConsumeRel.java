package com.neuromancer42.tea.commons.analyses.annotations;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConsumeRel {
    String name() default "";
    String[] doms();
    String description() default "";
}
