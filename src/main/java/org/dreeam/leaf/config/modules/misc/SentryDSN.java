package org.dreeam.leaf.config.modules.misc;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.dreeam.leaf.config.ConfigInfo;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.IConfigModule;

public class SentryDSN implements IConfigModule {

    @Override
    public EnumConfigCategory getCategory() {
        return EnumConfigCategory.MISC;
    }

    @Override
    public String getBaseName() {
        return "sentry_dsn";
    }

    @ConfigInfo(baseName = "sentry-dsn")
    public static String sentryDsn = "";

    @Override
    public void onLoaded(CommentedFileConfig config) {
        config.setComment("misc.sentry_dsn", """
                Sentry DSN for improved error logging, leave blank to disable,
                Obtain from https://sentry.io/welcome/
                """);

        String sentryEnvironment = System.getenv("SENTRY_DSN");
        sentryDsn = sentryEnvironment == null ? sentryDsn : sentryEnvironment;
        if (sentryDsn != null && !sentryDsn.isBlank()) {
            gg.pufferfish.pufferfish.sentry.SentryManager.init();
        }
    }
}
