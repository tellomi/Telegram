# Upstream patches

Official tree is DrKLO `3de37c8cfb039452be7398909461da6288babf15`. These `src/main` edits keep the installed `app.tellomi.chat` package readable. They do not restore Discover, story hiding, or email login.

- `ApplicationLoader.getFilesDirFixed` last-resort directory uses the installed package name.
- Contacts account type, sync adapter, contact MIME, and `LaunchActivity` call match are `app.tellomi.chat`.
- Saved-media directories and the connection-service group title use `BuildVars.APP_NAME` (`Tellomi`), the names `14.0.7` already wrote.
- Server connection stays in the native Tellomi DC hooks. `MessagesStorage`, `UserConfig`, and `tgnet/Config.cpp` are unchanged.

## Main bar menu settings

The bottom-right search button was Tellomi-only. DrKLO `3de37c8` `MainTabsActivity` builds `GlassTabView[5]` and has no `INDEX_SEARCH`. It was added in `fe3d5f525` and the icon wiring in `a9b4403c4`. That button, `DialogsActivity.openSearchFromMainTab`, and `GlassTabView.TabAnimation.SEARCH` are removed. `Moments_SearchTab` stays as the city-search hint.

Visibility stays on the existing pager indexes and `MainTabsLayout.setViewVisible` (the calls tab already used it). New code is `TellomiMainTabs`, `TellomiMainTabsStore`, and `TellomiMenuSettingsActivity`.

Upstream files touched for this:

- `MainTabsActivity` — drop the Tellomi search button; show or hide each tab from `TellomiMainTabs`, including the calls long-press, which still writes `UserConfig.showCallsTab`.
- `DialogsActivity` — drop the Tellomi search-tab listener. `closeSearching` itself stays.
- `GlassTabView` — drop `TabAnimation.SEARCH`. `setShowsLabel` hides the caption and centers the icon. The approach follows NagramX `setMainTabsCompact` (GPL-2.0, https://github.com/risin42/NagramX/commit/f32ff4e47f3d11e8358c9a30e7bcfd37570633d0) without copying that helper or shrinking the bar. iMe's public tree (GPL-2.0, https://github.com/imemessenger/iMe-Android, master only, last push 2021-02-13) has no 选单设定 screen. Cherrygram `MainTabsManager` was read and not reused; its header asks other forks not to copy it.
- `SettingsActivity` — a Tools header and a Menu settings row. Profile is unchanged: own-profile actions already call `addSettings()` and `KEY_SETTINGS` presents `SettingsActivity`, so Settings stays reachable when that tab is hidden.
