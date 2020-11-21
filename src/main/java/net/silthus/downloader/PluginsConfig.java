package net.silthus.downloader;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class PluginsConfig {

    private String curseApiKey = null;
    private Map<String, Plugin> plugins = new HashMap<>();
}
