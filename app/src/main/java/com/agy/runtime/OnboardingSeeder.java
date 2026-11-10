package com.agy.runtime;

import android.content.Context;
import android.util.Log;

import com.agy.util.AtomicFileWriter;

import java.io.File;
import java.io.InputStream;

/**
 * Materializes a fully-onboarded state before the core starts, so the Welcome
 * wizard is skipped. Idempotent and non-destructive: only writes when the
 * target is absent (or a zero-byte stub).
 */
public final class OnboardingSeeder {

    private static final String TAG = "OnboardingSeeder";

    private OnboardingSeeder() {
    }

    public static void seed(Context appContext, File appData, File projects) {
        try {
            if (!appData.exists() && !appData.mkdirs()) {
                Log.w(TAG, "cannot create " + appData);
                return;
            }
            seedStateFile(appContext, "jetski_state.pbtxt", new File(appData, "jetski_state.pbtxt"));
            seedStateFile(appContext, "antigravity_state.pbtxt",
                    new File(appData, "antigravity_state.pbtxt"));

            // Also seed directly into $HOME/.gemini/antigravity-cli/ where official agy CLI resolves state
            File geminiDir = appData.getParentFile();
            if (geminiDir != null) {
                File cliDir = new File(geminiDir, "antigravity-cli");
                if (cliDir.isDirectory() || cliDir.mkdirs()) {
                    seedStateFile(appContext, "jetski_state.pbtxt", new File(cliDir, "jetski_state.pbtxt"));
                    seedStateFile(appContext, "antigravity_state.pbtxt", new File(cliDir, "antigravity_state.pbtxt"));

                    // Seed settings.json if missing
                    File settingsFile = new File(cliDir, "settings.json");
                    if (!settingsFile.isFile()) {
                        AtomicFileWriter.writeText(settingsFile, "{\n  \"allowNonWorkspaceAccess\": true,\n  \"artifactReviewPolicy\": \"always-proceed\",\n  \"showFeedbackSurvey\": false,\n  \"showTips\": false,\n  \"toolPermission\": \"always-proceed\"\n}\n");
                    }
                }
            }

            // The project store resolves its projects here; without the
            // pseudo-project file it logs project_store_get_file_missing.
            if (!projects.exists() && !projects.mkdirs()) {
                Log.w(TAG, "cannot create " + projects);
                return;
            }
            File outside = new File(projects, "outside-of-project.json");
            if (!outside.isFile()) {
                AtomicFileWriter.writeText(outside, "{}\n");
            }
        } catch (Exception e) {
            Log.w(TAG, "onboarding seed failed (continuing): " + e.getMessage());
        }
    }

    private static void seedStateFile(Context appContext, String assetName, File target)
            throws Exception {
        if (target.isFile() && target.length() > 0) {
            if ("jetski_state.pbtxt".equals(assetName)) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(target)) {
                    String existing = com.agy.util.IoUtil.readAll(fis, "UTF-8");
                    if (existing != null && existing.contains("AGENT_ONBOARDING_STATE_COMPLETED")) {
                        Log.i(TAG, "state present and completed; left untouched: " + target);
                        return;
                    }
                    Log.i(TAG, "state incomplete; overwriting with fully onboarded seed: " + target);
                } catch (Exception ignored) {}
            } else {
                Log.i(TAG, "state present; left untouched: " + target);
                return;
            }
        }
        try (InputStream in = appContext.getAssets().open(
                RuntimePaths.SEED_ASSET + "/" + assetName)) {
            String content = com.agy.util.IoUtil.readAll(in, "UTF-8");
            AtomicFileWriter.writeText(target, content);
        }
        Log.i(TAG, "seeded onboarding state: " + target);
    }
}
