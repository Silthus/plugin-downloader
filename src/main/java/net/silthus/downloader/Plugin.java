package net.silthus.downloader;

import joptsimple.internal.Strings;
import lombok.Data;

import java.net.URI;

@Data
public class Plugin {

    private int bukkitId = -1;
    private String name;
    private String file;
    private String url;
    private String type;
    private String mcVersion;
    private String configUrl;
    private String branch;
    private boolean useToken = false;
    private ConfigType configType = ConfigType.EMPTY;

    public void load() {
        if (bukkitId > 0) {
            new CurseforgeApi(bukkitId, Builder.API_KEY).query().map(pluginInfo -> {
                if (!Strings.isNullOrEmpty(mcVersion) && !mcVersion.equalsIgnoreCase(pluginInfo.getGameVersion())) {
                    return null;
                }
                if (!Strings.isNullOrEmpty(type) && !type.equalsIgnoreCase(pluginInfo.getType())) {
                    return null;
                }
                return pluginInfo.getLink();
            }).ifPresent(this::setUrl);
        }
    }

    public static enum ConfigType {

        GIT,
        ZIP,
        EMPTY
    }
}
