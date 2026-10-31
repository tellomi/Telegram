# Tellomi 硬规则（Android / Telegram）

给后续 AI / 机器人 / 新同学看的固定规则。改规则开 PR，不要只改聊天记忆。同套规则也在 `tellomi/teamgram-server` 与 `tellomi/Telegram-iOS` 的 `docs/HARD_RULES.md`。

## 项目是什么

1. Tellomi 是面向中国大陆的陌生人交友 App；本仓是 Telegram Android 改牌客户端。
2. 服务器：`tellomi/teamgram-server`；iOS：`tellomi/Telegram-iOS`。

## 协作与发版

3. 合 PR、修 bug、构建：团队自行决定直接做，不用请示。
4. 完成一个阶段/PR 在「所有权限管理、cms goco」群推短讯（改动 + PR 链接）。
5. 不要完善一点就出包；攒批再测再发；双端齐再出正式测包（如 14.0.3）。闪退/发码可临时单验。
6. 密钥向「所有权限管理」索取；GitHub 连接 @3mmmbulus。

## 客户端改动

7. 改动最小化，对照上游；改动记入 UPSTREAM_PATCHES（或 Tellomi 等价文档）。
8. 别自写一套登录/设置布局；对照上游双端。
9. 登录默认区号 +86。
10. 登录改牌后勿留死引用（重命名节点后全局搜残留）。
11. 安装包保持瘦：贴纸/主题/大图 CDN 按需；别塞无用素材/语言；对照上游拆 ABI。

## 服务器地址（出包 checklist）

12. DC 只用 IP，不用主机名。生产/dev：`8.137.39.82:10443`（profile 名 `dev`）。模拟器 docker：`10.0.2.2:10443`（profile `local`，仅 debug）。
13. **可安装包（Release / CI 测包）禁止 `local` / `10.0.2.2`。** 必须 `TELLOMI_SERVER_PROFILE=dev`，出包前核 `BuildConfig` 的 `TELLOMI_SERVER_PROFILE` 与 `TELLOMI_DC_ADDRESSES`；CI 已强制 `-PTELLOMI_SERVER_PROFILE=dev` 并拒模拟器地址（见 #23）。
14. debug 仍可用 `-PTELLOMI_SERVER_PROFILE=local`。
15. 详见 `docs/TELLOMI.md` 与 `TMessagesProj/tellomi/ANDROID_APK.md`。

## 与服务器 API

16. 补齐/联调 API 时服务器侧对照官方 MTProto 文档；客户端覆盖口径只跟官方四端实际调用。
17. 「已实现」≠ 与 Telegram 公网一比一。
