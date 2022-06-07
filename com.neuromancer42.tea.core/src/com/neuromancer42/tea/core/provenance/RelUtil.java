package com.neuromancer42.tea.core.provenance;

import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.project.Project;

public class RelUtil {
    public static ProgramRel pRel(String name) {
        return (ProgramRel) Project.g().getTrgts().get(name);
    }
}
