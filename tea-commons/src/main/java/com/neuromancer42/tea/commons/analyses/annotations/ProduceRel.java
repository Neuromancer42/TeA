package com.neuromancer42.tea.commons.analyses.annotations;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ProduceRel {
    String name() default "";
    String[] doms();
    String description() default "";
}