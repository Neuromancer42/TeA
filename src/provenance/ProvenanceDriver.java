package provenance;

import chord.analyses.JavaAnalysis;
import chord.project.Config;
import chord.util.Utils;

import java.io.File;
import java.util.List;

public abstract class ProvenanceDriver extends JavaAnalysis {
    private Provenance provenance;

    @Override
    public void run() {
        String dlogName = getDlogName();
        ProvenanceBuilder pDriver = new ProvenanceBuilder(dlogName);
        pDriver.computeProvenance(getOutputRelationNames());
        provenance = pDriver.getProvenance();
        String dumpDirName = Config.v().outDirName + File.separator + "provenance";
        Utils.mkdirs(dumpDirName);
        provenance.dump(dumpDirName);
    }

    protected abstract List<String> getOutputRelationNames();
    protected abstract String getDlogName();
}
