package dansplugins.fiefs.testsupport;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.FakeMedievalFactionsApi;

/** Upstream command fixtures adapted to the published stable API, without MF internals. */
public final class MedievalFactionsFactions {
    private MedievalFactionsFactions() { }
    public static FactionView faction(String id, String name) {
        FakeMedievalFactionsApi api = new FakeMedievalFactionsApi();
        return api.getFaction(api.createFaction(id, name));
    }
}
