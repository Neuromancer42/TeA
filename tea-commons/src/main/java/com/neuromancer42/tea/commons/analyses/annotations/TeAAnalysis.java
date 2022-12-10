package com.neuromancer42.tea.commons.analyses.annotations;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TeAAnalysis {
    String name();
}
