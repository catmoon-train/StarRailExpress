package net.exmo.sre;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class LoadingAdapter implements LanguageAdapter {
    @Override
    @SuppressWarnings("unchecked")
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        if (type != PreLaunchEntrypoint.class) {
            throw new LanguageAdapterException("SRE adapter only supports PreLaunchEntrypoint");
        }
        return (T) (PreLaunchEntrypoint) () -> {
        };
    }

    static {
        try {

        } catch (Throwable t) {
            System.err.println("[SRE] Failed to initialize update check");
            t.printStackTrace();
        }
    }
}
