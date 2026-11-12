# Upstream patches

Official tree is DrKLO `3de37c8cfb039452be7398909461da6288babf15`. These `src/main` edits keep the installed `app.tellomi.chat` package readable. They do not restore Discover, story hiding, or email login.

- `ApplicationLoader.getFilesDirFixed` last-resort directory uses the installed package name.
- Contacts account type, sync adapter, contact MIME, and `LaunchActivity` call match are `app.tellomi.chat`.
- Saved-media directories and the connection-service group title use `BuildVars.APP_NAME` (`Tellomi`), the names `14.0.7` already wrote.
- Server connection stays in the native Tellomi DC hooks. `MessagesStorage`, `UserConfig`, and `tgnet/Config.cpp` are unchanged.

## Main bar menu settings

The bottom-right search button was Tellomi-only. DrKLO `3de37c8` `MainTabsActivity` builds `GlassTabView[5]` and has no `INDEX_SEARCH`. It was added in `fe3d5f525` and the icon wiring in `a9b4403c4`. That button, `DialogsActivity.openSearchFromMainTab`, and `GlassTabView.TabAnimation.SEARCH` are removed. `Moments_SearchTab` stays as the city-search hint.

Visibility stays on the existing pager indexes and `MainTabsLayout.setViewVisible` (the calls tab already used it). New code is `TellomiMainTabs`, `TellomiMainTabsStore`, and `TellomiMenuSettingsActivity`. Flags are booleans in `MessagesController.getGlobalMainSettings()` (`mainconfig`, the same file as `SharedConfig`): `mainTabsHideLabels`, `mainTabsContacts`, `mainTabsCalls`, `mainTabsFeed`, `mainTabsSettings`. Chats and Profile are not stored. A value saved under the previous `tellomi_main_tab_*` key is copied across on the first read.

Upstream files touched for this:

- `MainTabsActivity` — drop the Tellomi search button; show or hide each tab from `TellomiMainTabs`. The calls long-press still writes `UserConfig.showCallsTab` and posts `callTabsVisibleToggled`. Tab changes from menu settings post `mainTabsSettingsUpdated`.
- `DialogsActivity` — drop the Tellomi search-tab listener. `closeSearching` itself stays.
- `GlassTabView` — drop `TabAnimation.SEARCH`. `setShowsLabel` hides the caption and centers the icon. The approach follows NagramX `setMainTabsCompact` (GPL-2.0, https://github.com/risin42/NagramX/commit/f32ff4e47f3d11e8358c9a30e7bcfd37570633d0) without copying that helper or shrinking the bar. iMe's public tree (GPL-2.0, https://github.com/imemessenger/iMe-Android, master only, last push 2021-02-13) has no 选单设定 screen. Cherrygram `MainTabsManager` was read and not reused; its header asks other forks not to copy it.
- `SettingsActivity` — a Tools header and a Menu settings row. Copy is `LocaleController.getString(R.string.…)`. Profile is unchanged: own-profile actions already call `addSettings()` and `KEY_SETTINGS` presents `SettingsActivity`, so Settings stays reachable when that tab is hidden.
- `NotificationCenter` — append `mainTabsSettingsUpdated`. It is the last `totalEvents++`, so earlier event ids stay put.

## Production DC relay

Shippable seeds dial `8.137.198.240` on 10443, then 443, then `api.tellomi.cn` on those ports, for DC 1 and DC 2. `8.137.198.240` forwards that TCP. MTProto still terminates behind the relay, so `TMessagesProj/config/tellomi-server-public.pem` is the same file (fingerprint `4381149949997195609`).

`TELLOMI_PRODUCTION_HOST` is the only built-in production IP. `initDatacenters` adds it with flags 0. Download connections use `addressesIpv4Download` when that list is non-empty and otherwise the same generic list. Upload uses the generic list. CDN options and `help.getConfig` still replace those literals with whatever the server sends. A saved list that already starts with `8.156.40.105` stays first until that happens. `onRequestNewServerIpAndPort` still returns immediately when `TELLOMI_CUSTOM_BACKEND` is set.

`tellomiRefuseNonProductionShip` and `tellomiVerifyShippableDc` reject a seed that contains `8.156.40.105`.

## Skip the welcome carousel

`IntroActivity` (Tellomi mark, pager, “使用简体中文继续”, “Start Messaging”) stays in the tree. `TellomiIntro.SKIP_WELCOME` is the switch. Set it to false to show the carousel again.

Unauthenticated entry now opens `LoginActivity` as the only fragment:

- `LaunchActivity.getClientNotActivatedFragment` — phone screen when `TellomiIntro.openPhoneLogin` is true. The old `return new IntroActivity()` remains for the restore path. A resumed login step (`currentViewNum != 0`) already opened `LoginActivity`.
- `LaunchActivity.switchToAvailableAccountOrLogout` — last-account logout presents `LoginActivity` with no animation, so back is not the welcome page and not an empty container. `IntroActivity.setOnLogout()` remains in the else branch.
- Add account (`MainTabsActivity`, `LogoutActivity`, `UserInfoActivity`, `ProfileActivity`) and account switch already present `LoginActivity` or `switchToAccount`. They are unchanged. QR login stays `ActionIntroActivity.ACTION_TYPE_QR_LOGIN`. Email login stays `LoginActivity.changeEmail`. Phone country selection, including the existing +86 path, is unchanged. `LoginActivity` still takes its colors from the current theme.

System language: `LocaleController` construction, when no `mainconfig` `language` is saved, uses `TellomiIntro.matchShortName` before the English fallback. Simplified Chinese (`zh`, `zh_CN`, Hans) prefers `zh_hans` / `zh_cn`. Traditional (`zh_TW`, `zh_HK`, `zh_MO`, Hant) prefers `zh_hant` / `zh_tw`. After `langpack.getLanguages`, `applyLoadedLanguage` applies that row and does not write `language`. `saveRemoteLocaleStrings` uses the same rule when the remote file lands: `storeLanguageAfterRemoteSave` writes `language` only if Settings already stored a known language. An automatic choice stays unsaved, so the next launch can follow a different device locale. A saved language is applied with `override` and is not replaced.

## Profile Moments tab

On a user chat that is not the own-profile stories view and not story search, `updateTabs` already inserts `TAB_MOMENTS`. That block now also drops native story tabs (`TAB_STORIES`, archived stories, story albums) unless the stories slot is a bot preview. The presence check treats that omission as the steady state, so the strip is not rebuilt on every call. A missing Moments tab is itself a change, so a profile whose only stock tab was Stories still gets Moments. `scrollToPage` does not select a story tab that is no longer in the strip. `ProfileActivity`'s info rows are unchanged. `FeedProfilePage` is still the child added under the tab strip; its top margin stays non-negative, so the following and followers lines stay inside the shared-media row. The link that opens the post list uses `Moments_Profile_OpenPosts` (`All posts` / `帖子`) so it does not repeat the tab title `Moments_Profile_Posts` (`Posts` / `动态`).
