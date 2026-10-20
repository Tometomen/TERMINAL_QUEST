package jp.terminalquest.mission;

/** Success depends on world state and successful observations, never exact input text. */
public final class MissionEvaluator {
    public boolean evaluate(MissionSession session) {
        for (MissionCondition condition : session.getMission().conditions) {
            if (!condition.test(session)) return false;
        }
        return session.getMission().conditions.length > 0;
    }
}
