# AGENTS.md — Tellomi Android（tellomi/Telegram）

项目硬规则见 docs/TELLOMI_RULES.md

本仓职责：Telegram Android（DrKLO）的 Tellomi 客户端，连接 Teamgram，不连接 Telegram 数据中心。

## 必读（按顺序）

1. `docs/TELLOMI_RULES.md` —— 铁律（四仓同文，改规则开 PR）
2. 模块边界：https://github.com/tellomi/tellomi/blob/main/docs/MODULE_BOUNDARIES.md
3. `docs/TELLOMI.md` —— 本仓构建、DC、品牌
4. `TMessagesProj/tellomi/UPSTREAM_PATCHES.md` —— 对上游文件的改动记录
5. `TMessagesProj/tellomi/ANDROID_APK.md` —— 签名包与 `latest.json` 发布

## 禁区

- 新 Tellomi 逻辑放 `TMessagesProj/tellomi/`（`tellomi/java`、`tellomi/res`）；改 DrKLO 的 `src/main` 必须记入 `TMessagesProj/tellomi/UPSTREAM_PATCHES.md`，保持最小 diff
- 不提交密钥、私钥、真 `google-services.json`；占位符与公钥说明见 `docs/TELLOMI.md`
- 对人装的测包/Release 禁止打出 `10.0.2.2`；`-PTELLOMI_SERVER_PROFILE=local` 只允许 debug
- DC 列表只写 IP:端口，不写域名
- 不在 `AGENTS.md`、`.cursor/rules/tellomi.mdc`、`.github/copilot-instructions.md` 里另写规则正文；要改规则改 `docs/TELLOMI_RULES.md` 并开 PR

## 合入 / 出包闸门

- CI 绿 + 评审后 squash 合入；不直推 `master`
- 跨仓契约变更：契约 PR 先合，实现 PR 同批合齐
- 对人装的测包/Release：`./gradlew :TMessagesProj_App:assembleAfatRelease -PTELLOMI_SERVER_PROFILE=dev`，出包前跑 `./gradlew :TMessagesProj:tellomiVerifyShippableDc`，核 BuildConfig DC 为 `8.137.39.82:10443`
- 每次安卓 Release 发布 `https://www.tellomi.cn/dl/android/latest.json`（步骤见 `TMessagesProj/tellomi/ANDROID_APK.md`）；禁止只改生产 `.env` 来弹更新
- 双端测包齐再向 owner 报阶段完成
