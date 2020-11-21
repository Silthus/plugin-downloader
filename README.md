# Tales of Faldoria Build Tools

These `BuildTools` are mostly copied code from the [`Spigot BuildTools`](https://hub.spigotmc.org/stash/projects/SPIGOT/repos/buildtools/). See the [license](LICENSE.md) for details.

This project aims to fully automate the configuration and deployment of a full [Spigot](https://hub.spigotmc.org/) Minecraft server. It will download plugins and config of plugins based on a simple configuration file.

- [Getting Started](#getting-started)
  - [Parameters](#parameters)
- [Configuration](#configuration)
  - [Latest plugin from BukkitDev (Curseforge)](#latest-plugin-from-bukkitdev-curseforge)
- [References](#references)

## Getting Started

Start the Build Tools via `java -jar <JAR-FILE>`. In the defaults it will try to read the `plugins.yml` from the current directory as base config.

```bash
java -jar ToF-BuildTools.jar
```

### Parameters

The Build Tools take the following parameters.

| Parameters         | Description                                                                                        | Example                                                                 |
| ----------------- | --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| --config / -c     | Specify an alternative path to the `plugins.yml` config.                                            | `--config data/plugins.yml`                                              |
| --output-dir / -o | Specify the output directory into which plugins and configs are downloaded. Default: `plugins/`     | `--output-dir mods/`                                                     |
| --git-username    | If using private git repositories you can specify the username.                                     | `--git-username Silthus`                                                 |
| --git-password    | If using private git repositories you can specify a password or auth-token to download the configs. | `--git-password foobar`                                                  |
| --file-username   | Allows the specification for BASIC authentication when downloading files.                           | `--file-username Silthus`                                                |
| --file-password   | Allows the specification for BASIC authentication when downloading files.                           | `--file-password foorbar`                                                |
| --header / -h     | Adds additional headers when downloading files. The header will be split at `:`.                    | `-h PRIVATE-TOKEN: foobar`                                               |
| --configs         | Allows the specification of a suffix for config files.                                              | `--configs .plugin.yml` will use all configs that end with `.plugin.yml` |
| --dir / -d        | Optional directory to load configs with the `--configs` suffix from.                                | `-d /foobar`                                                             |

## Configuration

Everything is configured in the `plugins.yml` or the respective configuration files. The path and name can be changed via the `--config foobar.yml` parameter.

> All `.jar` files that are downloaded will be overwriten everytime.

In the config all plugins and their configs are specified by a download url and git repository or download url with a zip file of the config.

> Plugin configs that are downloaded from git repositories will be updated using `git pull`. Remember to commit your changes before updating the configs.

```yml
# You can provide your Curseforge BukkitDev API key from https://dev.bukkit.org/account/api-tokens to avoid 403 errors when downloading plugins from bukkit dev.
curseApikey: <your-api-key>
plugins:
  worldedit:
    file: WorldEdit.jar
    url: https://dev.bukkit.org/projects/worldedit/files/latest
    # to avoid passing sensitive tokens to random sites you need to set the
    # useToken to true when downloading from bukkit dev
    useToken: true
    configType: ZIP
    configUrl: https://pub.faldoria.de/plugin-configs/worldedit.zip
  # needs the api key to work properly
  worldedit:
     file: WorldEdit.jar
     bukkitId: 31043
  raidcraft-api:
    file: RaidCraft-API.jar
    url: https://git.faldoria.de/raidcraft/raidcraft-api/-/jobs/artifacts/master/raw/target/RaidCraft-API.jar?job=build
    configType: GIT
    branch: master
    configUrl: https://git.faldoria.de/plugin-configs/rcmobs.git
  custom-path-jar:
    # You can download the plugin into any sub directory you want
    file: RaidCraft-API/custom-plugin.jar
    url: https://foobar.faldoria.de/plugin.jar
    # The config paramters are optional
```

### Latest plugin from BukkitDev (Curseforge)

Since [Version 1.1.0](CHANGELOG.md#110) you can now download the latest version from [BukkitDev (Curseforge)](https://dev.bukkit.org) by specifying an `curseApiKey` and the `bukkitId` of the plugin. Get you API key from [BukkitDev](https://dev.bukkit.org/account/api-tokens) or else you will receive 403 errors after a couple of downloads.

> You can find the ID of the plugin on the right side of the BukkitDev plugin page.
>
> ![Bukkit Dev Plugin ID](docs/bukkitdev_id.PNG)

```yml
curseApiKey: <your-api-key-from-bukkitdev>
plugins:
  worldedit:
    file: WorldEdit.jar
    bukkitId: 31043
```

## References

Thanks to the following projects for inspiration and code contribution to the project:

* [Spigot BuildTools](https://hub.spigotmc.org/stash/projects/SPIGOT/repos/buildtools/browse)
* [Curseforge Servermods API Example](https://github.com/gravitylow/ServerModsAPI-Example) by [@gravitylow](https://github.com/gravitylow/)