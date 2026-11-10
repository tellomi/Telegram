# Moments feed layout

Android renders these tokens. iOS should match them. Colors come from the Telegram theme, including dark mode. Rows have no card fill.

Strings use the same `Moments.*` langpack keys as iOS. English wording is the `values/strings.xml` value. Simplified Chinese is `values-zh-rCN/strings.xml`. `FeedUi.s` asks `LocaleController` for the key, then the resource for the active language. If the UI language is Chinese and the langpack value is still the English source, the Simplified Chinese resource is shown.

## Card

| Token | Value |
| --- | --- |
| Page background | `windowBackgroundWhite` |
| Cell padding | 12 dp horizontal, 10 dp top, 4 dp bottom |
| Avatar | 36 dp circle, 10 dp gap before the name |
| Name | 15 sp, bold, `windowBackgroundWhiteBlackText`, one line |
| Time and audience | 13 sp, `windowBackgroundWhiteGrayText`, the line under the name |
| More | 40 dp, top end |
| Body | chat font size (`SharedConfig.fontSize`), primary text |
| Text column | 46 dp inset inside the cell, so 12 + 36 + 10 from the screen edge |
| Fold | 5 lines, then “Full text” |
| Divider | 1 px, `divider` |

## Media

The grid sits in the text column, 8 dp below the text, 3 dp gaps.

| Count | Layout |
| --- | --- |
| 1 photo | full column width, height from the aspect ratio, clamped to 120–360 dp, 12 dp corners |
| 1 video | full column width, height `max(180 dp, width * 9 / 16)`, 12 dp corners |
| 2 or 4 | two columns, square cells, 8 dp corners |
| 3, 5, 6, 7, 8, 9, 10 | three columns, square cells, 8 dp corners |

Link, quote, poll, and file cards use `graySection`, 8 dp corners, padding 10 dp horizontal and 8 dp vertical.

## Actions

| Token | Value |
| --- | --- |
| Row inset | 40 dp from the cell’s left padding |
| Icon | 40 dp, `dialogIcon` |
| Count | 13 sp, 8 dp after the number |
| Order | like, count, comment, count, repost, share, save |
| Liked heart | `#FF2E38` |
| Saved icon | `featuredStickers_addButton` |

There are no text labels on the icons. The content description uses the `Moments.*` string.

## Tabs and compose

| Token | Value |
| --- | --- |
| Tab row | 40 dp, 14 sp bold |
| Selected tab | `windowBackgroundWhiteBlackText` |
| Other tabs | `windowBackgroundWhiteGrayText` |
| Tab background | `windowBackgroundWhite` |
| Compose button | 56 dp circle, 20 dp from the trailing edge, 16 dp above the main tab bar |

## Post detail

The post uses the same cell, with the body unfolded. Comments are rows on that screen, not a bottom sheet: 16 dp horizontal padding, name 15 sp bold, time 13 sp secondary, body at the chat font size, and a 1 px divider.
