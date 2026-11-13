# Upstream patches

## Profile tab label (M2 / ISSUE-A-NAV-001)

After signup the visible bar is Discover, Chats, then the avatar tab. That tab used `R.string.MainTabsProfile`. Cloud strings replace a key the downloaded langpack contains, and the applied zh-Hans value of `MainTabsProfile` is 个人资料. The product caption is 我. `TellomiTabMe` is not an official key (`Me` / `我` / `我`), so the built-in string is the one that shows. The menu-settings row for the same tab uses that key too. `MainTabsProfile` itself is unchanged.

Upstream file: `MainTabsActivity` — the avatar tab’s string resource.


## Native feature audit 2026-11-14

Teamgram `master` implements the folder, sticker, and connection methods the official client leaves off until `help.getAppConfig`. This server does not send `dialog_filters_enabled`, `background_connection`, `keep_alive_service`, or `save_gifs_with_stickers`, so those DrKLO defaults stayed off. `TellomiNativeFeatures` turns them on. `help.getAppConfig` can still write the key later.

Composer AI follows `tellomi_ai_compose_enabled` (false until teamgram advertises `TELLOMI_AI_COMPOSE`). Manual and automatic translation start at `disabled` and follow `translations_manual_enabled` / `translations_auto_enabled`, which the server sets to `enabled` only when the self-hosted translator is healthy.

Left off, matching the server's hide keys and the product list: Premium purchase (`premiumLocked` defaults blocked; `IS_BILLING_UNAVAILABLE`), Stars and gifts, the TON wallet (`wallet_available` defaults false, including debug), and Fragment entry points (`fragmentLinksAllowed` is false). Stories, secret chats, calls, business, and the existing tab/welcome decisions are unchanged. Passkeys stay off: the server stores them in memory, uses relying party `localhost`, and `auth.finishPasskeyLogin` does not return `auth.authorization`. `messages.summarizeText` returns an empty `textWithEntities`.

Upstream files:

- `MessagesController` — folder, background connection, keep-alive, GIF-with-sticker, Premium purchase, paid Stars, gifts, and translation defaults.
- `AppGlobalConfig` — `tellomi_ai_compose_enabled` defaults false; `wallet_available` defaults false.
- `BuildVars` — Google Play Billing stays unavailable.
- `ConnectionsManager` — push connection uses the same background-connection default when the user has not toggled it.
- `ApplicationLoader` — the notification keep-alive service uses the same default.
- `ChatActivityEnterView`, `ChatAttachAlert`, `CaptionPhotoViewer`, `RichEditor`, `RichEditorToolbar`, `ChatAttachAlertRichLayout` — AI buttons call `TellomiNativeFeatures.aiComposeEnabled`.
- `ChangeUsernameActivity`, `ChannelCreateActivity`, `ChatEditTypeActivity`, `ChatActivity`, `ProfileActivity`, `WalletActivity`, `StarGiftSheet` — Fragment links call `fragmentLinksAllowed`.

## Message bubble accessibility date

`ChatMessageCell.onInitializeAccessibilityNodeInfo` used to pass `TodayAt + " " + time` into `AccDescrSentDate` and `AccDescrReceivedDate`. English is `Sent %s` / `at`, so the spoken line is “Sent at HH:mm”. The zh-Hans langpack (`AccDescrSentDate` = `已发送于 %s`, `TodayAt` = `于`) then speaks “已发送于 于 HH:mm”. zh-Hant does the same with `傳送於 %s` and `於`. `AccDescrReceivedDate` (`已收到 %s` / `收到了 %s`) does not already contain that preposition.

The chat list (`DialogCell`) passes `formatDateAudio` into the same sent-date key. For today that is `TodayAtFormatted`, which zh-Hans translates as `%1$s` with no `于`, so “已发送于 HH:mm” is already correct there. Removing `于` from the langpack string would fix the bubble and drop it from the chat list. `TellomiAccDescr.sentOrReceivedAt` keeps the preposition when the format does not already end with it, and collapses a doubled CJK `于` / `於` when a translation glues them together (`已发送于%s`).

Upstream file: `ChatMessageCell`.

## Earlier patches

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

Native Stories stay on the profile. Archive and album tabs are unchanged. The bottom bar has no Stories item; its feed tab is `Moments_Title` (`Moments` / `动态`). Discover uses `Moments_Story` for a post kind (`Story` / `故事`). Neither place says `快拍`. The profile Stories tab still uses that name in Tellomi, so it must not share the Moments title.

`ProfileStories` in the bundled strings is `Stories` / `快拍`. A downloaded langpack replaces a key it contains, and the official simplified value of `ProfileStories` is `动态`. The Moments insertion in `updateTabs` therefore sets the existing `TAB_STORIES` title to `Moments_Profile_Stories` (`Stories` / `快拍`) unless that slot is a bot preview. The tab is not removed. `ProfileActivity`'s open animation is unchanged. The post-list row stays `Moments_Profile_OpenPosts` (`All posts` / `帖子`).

## Offline profile

Opening a profile from a chat avatar or a post avatar must show the cached `TLRPC.User` / `UserFull` immediately. `users.getFullUser` and `GET /v1/feed/profiles/{id}/posts` refresh in the background. Neither request holds the fragment at alpha 0, and neither request is required before back.

`ProfileActivity.needDelayOpenAnimation` no longer returns true while `playProfileAnimation == 0`. That path is the one used when the avatar animation is not playing (another member in a group, a feed author). The fragment used to sit at alpha 0 until `userInfo` arrived. Offline, `users.getFullUser` does not arrive, so the page stayed blank and back was swallowed by the unfinished transition.

`sharedMediaData` has nine message-media columns. `TAB_MOMENTS` is 16. With no cached `UserFull`, the stories tab stays off (`stories_pinned_available` is on `UserFull`). A saved post homepage then becomes the first tab and `switchToCurrentSelectedMode` indexed `sharedMediaData[16]` while the profile view was being created. That threw on the UI thread, so the open never finished and back did not complete. Official Telegram has no posts tab, so the same offline open only shows the cached user. `ProfileOffline.usesSharedMediaArray` keeps tab 16 out of that array. The scroll loader uses the same guard.

Cached rows:

- A feed row avatar uses only the in-memory user. A miss or a min user starts one `MessagesStorage.getUser` on the storage queue and returns immediately. `ProfileOffline.StoredReadGate` remembers an in-flight or finished read, including a miss, so later binds of that author do not read again. The storage thread posts the user back to the UI thread, `putUser(..., true)`, and rebinds the visible avatars.
- Opening a profile reads that same gate at most once. `FeedUi.seedCachedUser` waits up to `ProfileOffline.STORED_USER_WAIT_MS` (1500 ms) when it has to start or join the read, then sets `author_row_checked`. `ProfileActivity` skips its own read in that case. A chat avatar still uses the one bounded wait. A wedged queue returns and the page uses whatever is already in memory. A min user is not treated as the cached row until that single read has finished; `putUser` from cache replaces it.
- Memory miss for `UserFull` calls the existing `loadUserInfo` (user_settings). `loadFullUser` still runs afterwards and is not awaited.
- A feed author on the profile-open path resolves the user before any stub: the in-memory user, then the logged-in user from `UserConfig` when the id matches, then that single users-table read. A stub is inserted only when both are absent, the read finished, and the id is not the logged-in user. The stub is `min` and is passed to `putUser` with `fromCache=true`, so it cannot replace a full user or write `UserConfig.setCurrentUser`. List binding does not insert that stub. Blank name, username, and access hash are still copied onto that user. A name already in the cache is left as it is. `putUser(..., false)` after that copy is skipped for a min stub and for the logged-in user id.
- `loadProfilePosts` does not start the HTTPS call when `ApplicationLoader.isNetworkOnline()` is false. `keepProfilePosts` leaves the last confirmed bit and clears the in-flight probe.

`onBackPressed(boolean)` calls `BaseFragment.onBackPressed`. That closes a shown sheet or story viewer only when `invoked` is true (the same rule as `ChatActivity`) and returns false while the sheet is up, so a predictive back that has only passed the 1.5% threshold does not dismiss it. Tab reorder and media action mode are unchanged. The method does not look at the network. When nothing local is open it returns true, so a committed or hardware back still finishes the page.

Upstream files touched: `ProfileActivity`, `SharedMediaLayout`.

The posts tab follows the same rule as `hasMedia[i] > 0` in `SharedMediaLayout.updateTabs`, and stories still follow `stories_pinned_available`. `FeedProfileTabs.show` adds `TAB_MOMENTS` only after a confirmed homepage, and `FeedProfileTabs.insertIndex` places it immediately after `TAB_STORIES` when that tab is present. `FeedContract.profilePostsVisible` keeps the previous "has posts" bit when `GET /v1/feed/profiles/{id}/posts` fails. `FeedController.ensureProfilePosts` returns before the cache read and the request, then `updateTabs` runs again. Opening the profile and `onResume` each re-read the cache and refresh the homepage. `updateTabs` does not start that fetch. `FeedProfileProbe` keeps one flight per peer and backs off after a failure. The layout callback is removed in `onDestroy` and ignored once the view is detached. Deleting the loaded page does not count as an empty profile while a next-page cursor remains. The list itself is `FeedProfilePage` hosting `FeedListView` inside the media page, the same way gifts and saved messages are hosted. Its top padding is `getPagePaddingTop`, so the rows clear the name the way the media grid does. Avatar and title layout stay in `ProfileActivity`.
