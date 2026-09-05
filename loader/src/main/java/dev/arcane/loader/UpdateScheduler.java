package dev.arcane.loader;

@FunctionalInterface
interface UpdateScheduler {
    void schedule(UpdatePlan plan, ReleaseManifest release) throws Exception;
}
