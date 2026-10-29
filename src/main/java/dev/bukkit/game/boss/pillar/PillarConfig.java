package dev.bukkit.game.boss.pillar;

public record PillarConfig(
        double maxHp,
        int height,
        int breakTicks,
        double holdThreshold,
        double repairHpPerResource,
        double mobDamageScale,
        double aggroPercent,
        int aggroIntervalTicks,
        double tautSag,
        double slackSag) {

    public static PillarConfig defaults() {
        return new PillarConfig(100.0, 12, 40, 0.95, 10.0, 1.0, 40.0, 40, 0.02, 0.45);
    }

    public PillarConfig {
        maxHp = Math.max(1.0, maxHp);
        height = Math.max(1, height);
        breakTicks = Math.max(1, breakTicks);
        holdThreshold = Math.max(0.01, Math.min(1.0, holdThreshold));
        repairHpPerResource = Math.max(0.1, repairHpPerResource);
        mobDamageScale = Math.max(0.0, mobDamageScale);
        aggroPercent = Math.max(0.0, Math.min(100.0, aggroPercent));
        aggroIntervalTicks = Math.max(1, aggroIntervalTicks);
        tautSag = Math.max(0.0, tautSag);
        slackSag = Math.max(tautSag, slackSag);
    }
}
